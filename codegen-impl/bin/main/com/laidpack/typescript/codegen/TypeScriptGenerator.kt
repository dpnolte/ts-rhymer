package com.laidpack.typescript.codegen


import com.laidpack.typescript.codegen.moshi.ITargetType
import com.squareup.kotlinpoet.ClassName
import java.time.LocalDateTime
import java.util.*


/** Generates a JSON adapter for a target bodyType. */

internal class TypeScriptGenerator private constructor (
        target: ITargetType,
        private val typesWithinScope: Set<String>,
        customTransformers: List<TypeTransformer> = listOf(),
        private val currentModuleName: String,
        private val superTypeTransformer: SuperTypeProcessor
    ) {
    private val className = target.name
    private val typeVariables = target.typeVariables
    private val isEnum = target.isEnum
    private val superTypes = target.superTypes
    val output by lazy {generateDefinition()}
    private val propertiesOrEnumValues = target.propertiesOrEnumValues.values
    private val transformer = TypeScriptTypeTransformer(customTransformers)

    override fun toString(): String {
        return output
    }

    private fun generateInterface(): String {
        val extendsString = generateExtends()
        val templateParameters = generateTypeVariables()

        val properties= generateProperties()
        return "${indent}interface ${className.simpleName}$templateParameters$extendsString {\n" +
                    properties +
                "$indent}\n"
    }

    private fun generateProperties(): String {
        return propertiesOrEnumValues
                .joinToString ("") { property ->
                    val propertyName = property.jsonName()
                    val propertyType = transformer.transformType(property.bodyType, typesWithinScope, typeVariables)
                    val isNullable = if (transformer.isNullable(property.bodyType)) "?" else ""
                    "$indent$indent$propertyName$isNullable: $propertyType;\n"
                }
    }

    private fun generateEnum(): String {
        val enumValues= propertiesOrEnumValues.joinToString(", ") { enumValue ->
            "'${enumValue.jsonName()}'"
        }
        return "${indent}enum ${className.simpleName} { $enumValues }\n"
    }

    private fun generateExtends(): String {
        return if (superTypes.isNotEmpty()) {
            " extends " + superTypes.joinToString(", ") { superTypeTransformer(it.className, currentModuleName) }
        } else ""
    }

    private fun generateTypeVariables(): String {
        return if (typeVariables.isNotEmpty()) {
            "<" + typeVariables.values.joinToString(", ") { typeVariable ->
                "${typeVariable.name}${getTypeVariableBoundIfAny(typeVariable)}"
            } + ">"
        } else {
            ""
        }
    }

    private fun getTypeVariableBoundIfAny(bodyType: IWrappedBodyType): String {
        val bounds = bodyType.bounds.values.filter { !(it nameEquals Any::class) }
        if (bounds.isNotEmpty()) {
            val joinedString = bounds.joinToString(" & ") {
                transformer.transformType(it, typesWithinScope, typeVariables)
            }
            return " extends $joinedString"
        }
        return ""
    }


    private fun generateDefinition(): String {
        return if (isEnum) {
            generateEnum()
        } else {
            generateInterface()
        }
    }

    companion object {
        private var indent = "  "
        fun generate(
                moduleName: String,
                targetTypes: HashMap<String, ITargetType>,
                indent: String,
                customTransformers: List<TypeTransformer>,
                constrainToCurrentModulePackage: Boolean,
                rootPackageNames: Set<String>,
                packageNames: Set<String>,
                filePreProcessors: List<FileProcessor>,
                filePostProcessors: List<FileProcessor>,
                definitionPreProcessors: List<DefinitionProcessor>,
                definitionPostProcessors: List<DefinitionProcessor>,
                superTypeTransformer: SuperTypeProcessor
        ): String {
            this.indent = indent
            val targetTypeNames = targetTypes.keys
            val definitions = mutableListOf<String>()
            targetTypeNames.sorted().forEach { key ->
                val targetType = targetTypes[key] as ITargetType
                if (isValidTargetType(targetType, constrainToCurrentModulePackage, rootPackageNames)) {
                    val generatedTypeScript = TypeScriptGenerator(
                            targetType,
                            targetTypeNames,
                            customTransformers,
                            moduleName,
                            superTypeTransformer
                    )
                    addAnyProcessedDefinitions(targetType, definitionPreProcessors, definitions)
                    definitions.add(generatedTypeScript.output)
                    addAnyProcessedDefinitions(targetType, definitionPostProcessors, definitions)
                }
            }
            val timestamp = "/* generated @ ${LocalDateTime.now()} */\n"
            val customBeginStatements = getAnyProcessedFileStatements(targetTypes, rootPackageNames, packageNames, filePreProcessors)
            val moduleStart = "declare module \"$moduleName\" {\n"
            val moduleContent = definitions.joinToString("\n")
            val moduleEnd = "}\n"
            val customEndStatements = getAnyProcessedFileStatements(targetTypes, rootPackageNames, packageNames, filePostProcessors)

            return "$timestamp$customBeginStatements$moduleStart$moduleContent$moduleEnd$customEndStatements"
        }

        private fun isValidTargetType(
                targetType: ITargetType,
                constrainToCurrentModulePackage: Boolean,
                rootPackageNames: Set<String>
        ): Boolean {
            return !constrainToCurrentModulePackage
                    || rootPackageNames.contains(targetType.name.packageName)
                    || rootPackageNames.any { targetType.name.packageName.startsWith(it) }
        }
        private fun addAnyProcessedDefinitions(
                targetType: ITargetType,
                processors: List<DefinitionProcessor>,
                definitions: MutableList<String>
        ) {
            for (processor in processors) {
                val result = processor(targetType)
                result?.let { definitions.add(it) }
            }
        }

        private fun getAnyProcessedFileStatements(
                targetTypes: HashMap<String, ITargetType>,
                rootPackageNames: Set<String>,
                packageNames: Set<String>,
                processors: List<FileProcessor>
        ): String {
            var result = ""
            for (processor in processors) {
                processor(targetTypes, rootPackageNames, packageNames)?.let {
                    result += it
                }
            }
            return result
        }
    }
}

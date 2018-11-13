package com.laidpack.typescript.codegen


import com.laidpack.typescript.codegen.moshi.ITargetType
import java.time.LocalDateTime
import java.util.*


/** Generates a JSON adapter for a target bodyType. */
internal enum class ModuleOption {
    Module,
    Namespace,
    None
}

internal class TypeScriptGenerator private constructor (
        target: ITargetType,
        private val typesWithinScope: Set<String>,
        customTransformers: List<TypeTransformer> = listOf(),
        private val currentModuleName: String,
        private val definitionTypeTransformer: DefinitionTypeTransformer,
        private val superTypeTransformer: SuperTypeTransformer,
        exportDefinitions: Boolean,
        private val indentBase: String = indent
    ) {
    private val className = target.name
    private val typeVariables = target.typeVariables
    private val isEnum = target.isEnum
    private val superTypes = target.superTypes
    val output by lazy {generateDefinition()}
    private val propertiesOrEnumValues = target.propertiesOrEnumValues.values
    private val transformer = TypeScriptTypeTransformer(customTransformers)
    private val export = if (exportDefinitions) "export " else ""

    override fun toString(): String {
        return output
    }

    private fun generateDefinition(): String {
        return if (isEnum) {
            generateEnum()
        } else {
            generateInterface()
        }
    }

    private fun generateInterface(): String {
        val extendsString = generateExtends()
        val templateParameters = generateTypeVariables()
        val interfaceName = definitionTypeTransformer(className)
        val properties= generateProperties()
        return "$indentBase${export}interface $interfaceName$templateParameters$extendsString {\n" +
                    properties +
                "$indentBase}\n"
    }

    private fun generateEnum(): String {
        val enumValues= propertiesOrEnumValues
                .sortedBy { it.jsonName() }
                .joinToString(",\n") { enumValue ->
                    "${indentBase+indent}${enumValue.jsonName()} = '${enumValue.jsonName()}'"
                }
        val enumName = definitionTypeTransformer(className)
        return "$indentBase${export}enum $enumName {\n" +
            "$enumValues\n" +
            "$indentBase}\n"
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

    private fun generateProperties(): String {
        return propertiesOrEnumValues
                .joinToString ("") { property ->
                    val propertyName = property.jsonName()
                    val propertyType = transformer.transformType(property.bodyType, typesWithinScope, typeVariables)
                    val isNullable = if (transformer.isNullable(property.bodyType)) "?" else ""
                    "$indentBase$indent$propertyName$isNullable: $propertyType;\n"
                }
    }

    companion object {
        private var indent = "  "
        fun generate(
                moduleName: String,
                moduleOption: ModuleOption,
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
                definitionTypeTransformer: DefinitionTypeTransformer,
                superTypeTransformer: SuperTypeTransformer,
                exportDefinitions: Boolean,
                inAmbientDefinitionFile: Boolean
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
                            definitionTypeTransformer,
                            superTypeTransformer,
                            exportDefinitions,
                            if (moduleOption == ModuleOption.None) {
                                ""
                            } else indent
                    )
                    addAnyProcessedDefinitions(targetType, definitionPreProcessors, definitions)
                    definitions.add(generatedTypeScript.output)
                    addAnyProcessedDefinitions(targetType, definitionPostProcessors, definitions)
                }
            }
            val timestamp = "/* generated @ ${LocalDateTime.now()} */\n"
            val customBeginStatements = getAnyProcessedFileStatements(targetTypes, rootPackageNames, packageNames, filePreProcessors)
            val declare = if (inAmbientDefinitionFile) {
                "declare "
            } else ""
            val export = if (exportDefinitions) "export " else ""
            val moduleStart = when(moduleOption) {
                ModuleOption.Module -> "$export${declare}module \"$moduleName\" {\n"
                ModuleOption.Namespace -> "$export${declare}namespace $moduleName {\n"
                else -> ""
            }
            val moduleContent = definitions.joinToString("")
            val moduleEnd = if (moduleOption == ModuleOption.None) "" else "}\n"
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
                    || rootPackageNames.any { targetType.name.packageName.startsWith("$it.") }
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

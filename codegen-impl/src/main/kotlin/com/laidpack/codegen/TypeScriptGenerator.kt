package com.laidpack.codegen


import com.laidpack.codegen.moshi.TargetType
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.classKind
import java.time.LocalDateTime
import java.util.*


/** Generates a JSON adapter for a target type. */

internal class TypeScriptGenerator private constructor (
        target: TargetType,
        private val typesWithinScope: Set<String>,
        customTypeScriptValueTransformers: List<ValueTypeTransformer> = listOf()
    ) {
    private val className = target.name
    private val typeVariables = target.typeVariables
    private val isEnum = target.proto.classKind == ProtoBuf.Class.Kind.ENUM_CLASS
    private val superTypes = target.superTypes
    val output by lazy {generateDefinition()}
    private val propertiesOrEnumValues = target.propertiesOrEnumValues.values
    private val transformer = TypeScriptTypeTransformer(customTypeScriptValueTransformers)

    override fun toString(): String {
        return output
    }

    private fun generateInterface(): String {
        val extendsString = generateExtends()
        val templateParameters = generateTemplateParameters()

        val properties= generateProperties()
        return "${indent}interface ${className.simpleName}$templateParameters$extendsString {\n" +
                    properties +
                "$indent}\n"
    }

    private fun generateProperties(): String {
        return propertiesOrEnumValues
                .filter { it != null }
                .joinToString ("") { property ->
                    val propertyName = property.jsonName()
                    val propertyType = transformer.transformType(property.type, typesWithinScope, typeVariables)
                    val isNullable = if (property.type.nullable) "?" else ""
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
            " extends " + superTypes.joinToString(", ") { it.element.simpleName }
        } else ""
    }

    private fun generateTemplateParameters(): String {
        return if (typeVariables.isNotEmpty()) {
            "<" + typeVariables.values.joinToString(", ") { templateParam ->
                "${templateParam.name}${getTemplateParamBoundIfAny(templateParam)}"
            } + ">"
        } else {
            ""
        }
    }

    private fun getTemplateParamBoundIfAny(type: WrappedType): String {
        val bounds = type.bounds.values.filter { !(it nameEquals Any::class) }
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
                targetTypes: HashMap<String, TargetType>,
                indent: String,
                customTypeScriptValueTransformers: List<ValueTypeTransformer> = listOf(),
                generateOnlyWithinModule: Boolean = false,
                rootPackageNames: Set<String> = setOf()
        ): String {
            this.indent = indent
            val targetTypeNames = targetTypes.keys
            val definitions = mutableListOf<String>()
            targetTypeNames.sorted().forEach { key ->
                val targetType = targetTypes[key] as TargetType
                if (!generateOnlyWithinModule
                        || rootPackageNames.contains(targetType.name.packageName)
                        || rootPackageNames.any { targetType.name.packageName.startsWith(it) }
                ) {
                    val generatedTypeScript = TypeScriptGenerator(
                            targetType,
                            targetTypeNames,
                            customTypeScriptValueTransformers
                    )
                    definitions.add(generatedTypeScript.output)
                }
            }
            val timestamp = "/* generated @ ${LocalDateTime.now()} */\n"
            val moduleStart = "declare module \"$moduleName\" {\n"
            val moduleContent = definitions.joinToString("\n")
            val moduleEnd = "}\n"

            return "$timestamp$moduleStart$moduleContent$moduleEnd"
        }
    }
}

package com.laidpack.codegen


import com.laidpack.codegen.moshi.TargetType
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.classKind
import java.time.LocalDateTime
import java.util.*


/** Generates a JSON adapter for a target type. */

internal class TypeScriptGenerator private constructor (
        target: TargetType,
        private val typesWithinScope: Set<String>
    ) {
    private val className = target.name
    private val typeVariables = target.typeVariables
    private val isEnum = target.proto.classKind == ProtoBuf.Class.Kind.ENUM_CLASS
    private val superTypes = target.superTypes

    var output = ""

    private val propertiesOrEnumValues = target.propertiesOrEnumValues.values

    init {
        output = generateDefinition()
    }

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
        return propertiesOrEnumValues.joinToString ("") { property ->
            val propertyName = property.jsonName()
            val propertyType = TypeScriptTypeTransformer.transformType(property.type, typesWithinScope, typeVariables)
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
                TypeScriptTypeTransformer.transformType(it, typesWithinScope, typeVariables)
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
        fun generate(moduleName: String, targetTypes: HashMap<String, TargetType>, indent: String): String {
            this.indent = indent
            val targetTypeNames = targetTypes.keys
            val sortedTypeNames = targetTypeNames.sorted()

            val timestamp = "/* generated @ ${LocalDateTime.now()} */\n"
            val moduleStart = "declare module \"$moduleName\" {\n"
            val moduleContent = sortedTypeNames.joinToString("\n") { key ->
                var generatedTypeScript = TypeScriptGenerator(targetTypes[key]!!, targetTypeNames)
                generatedTypeScript.output
            }
            val moduleEnd = "}\n"

            return "$timestamp$moduleStart$moduleContent$moduleEnd"
        }
    }
}

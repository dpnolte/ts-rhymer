package com.laidpack.codegen

class ValueTypeTransformer (val predicate: (type: IWrappedType) -> Boolean, val type: String)
//class NameTypeTransformer (val tranformer: (type: WrappedType) -> String )

internal class TypeScriptTypeTransformer(
        private val customValueTransformers: List<ValueTypeTransformer> = listOf()
) {

    fun transformType(type: WrappedType, typesWithinScope: Set<String>, typeVariables: Map<String, WrappedType>): String {
        val customValueTransformer = customValueTransformers.find { t -> t.predicate(type) }
        return when {
            customValueTransformer != null -> customValueTransformer.type
            type.isWildCard -> "any"
            type.name != null && typesWithinScope.contains(type.name as String) -> "${type.name}${transformTypeParameters(type, typesWithinScope, typeVariables)}"
            type.isReturningTypeVariable -> "${type.name}"
            type.isTypeVariable && typeVariables.containsKey(type.name) -> "${type.name}${transformTypeParameters(type, typesWithinScope, typeVariables)}"
            else -> {
                val transformer = if (!type.hasParameters) valueTransformers.find { t -> t.predicate(type) } else null
                transformer?.type
                        ?: transformCollectionType(type, typesWithinScope, typeVariables)
                        ?: "any /* unknown type */"
            }
        }
    }

    private fun transformCollectionType(type: WrappedType, typesWithinScope: Set<String>, typeVariables: Map<String, WrappedType>): String? {
        when {
            type.isMap && type.hasParameters -> {
                // check if first parameter is string or number.. then we can just use object notation
                val firstParam = type.firstParameterType
                val secondParam = type.secondParameterType
                return when {
                    firstParam nameEquals String::class -> "{ [key: string]: ${transformType(secondParam, typesWithinScope, typeVariables)} }"
                    numericClasses.any { c -> firstParam nameEquals c } -> "{ [key: number]: ${transformType(secondParam, typesWithinScope, typeVariables)} }"
                    else -> "Map${transformTypeParameters(type, typesWithinScope, typeVariables)}"
                }
            }
            type.isMap -> {
                return "Map<any, any>"
            }
            (type.isIterable || type.isArray) && type.hasParameters -> {
                if (type.parameters.size == 1) { // can it be something else?
                    return "Array${transformTypeParameters(type, typesWithinScope, typeVariables)}"
                }
            }
            type.isIterable -> {
                return "Array<any>"
            }
            type.isArray -> {
                return "any:[]"
            }
            type.isPair && type.hasParameters -> {
                val firstParam = type.firstParameterType
                val secondParam = type.secondParameterType
                return "[${transformType(firstParam, typesWithinScope, typeVariables)}, ${transformType(secondParam, typesWithinScope, typeVariables)}]"
            }
            type.isPair -> {
                return "[any, any]"
            }
            type.isSet && type.hasParameters -> {
                return "Set${transformTypeParameters(type, typesWithinScope, typeVariables)}"
            }
            type.isSet -> {
                return "Set<any>"
            }
        }

        return null
    }

    private fun transformTypeParameters(type: WrappedType, typesWithinScope: Set<String>, typeVariables: Map<String, WrappedType>): String {
        if (type.hasParameters) {
            return "<${type.parameters.values.joinToString(", ") {
                    transformType(it, typesWithinScope, typeVariables)
                }
            }>"
        }
        return ""
    }

    companion object {
        private val numericClasses = listOf(
                Int::class,
                Long::class,
                Short::class,
                Float::class,
                Double::class,
                Byte::class)
        private val valueTransformers = listOf(
                ValueTypeTransformer({ r -> r nameEquals String::class}, "string"),
                ValueTypeTransformer({ r -> r nameEquals Char::class}, "string"),
                ValueTypeTransformer({ r -> r nameEquals Int::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Long::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Short::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Float::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Double::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Byte::class}, "number"),
                ValueTypeTransformer({ r -> r nameEquals Boolean::class}, "boolean"),
                ValueTypeTransformer({ r -> r nameEquals IntArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals ShortArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals ByteArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals LongArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals DoubleArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals FloatArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals LongArray::class }, "Array<number>"),
                ValueTypeTransformer({ r -> r nameEquals CharArray::class }, "Array<string>"),
                ValueTypeTransformer({ r -> r nameEquals CharArray::class }, "Array<string>"),
                ValueTypeTransformer({ r -> r nameEquals  Any::class }, "any")
        )
    }
}
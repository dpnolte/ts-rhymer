package com.laidpack.typescript.codegen

enum class Nullability {
    Null,
    NonNull,
    NoTransform
}
class TypeTransformer (
        val predicate: (bodyType: IWrappedBodyType) -> Boolean,
        val type: String,
        val nullable: Nullability
)

internal class TypeScriptTypeTransformer(
        private val customTransformers: List<TypeTransformer> = listOf()
) {

    fun isNullable(bodyType: IWrappedBodyType): Boolean {
        val customTransformer = customTransformers.find { t -> t.predicate(bodyType) }
        return if (customTransformer != null) {
            when (customTransformer.nullable) {
                Nullability.Null -> true
                Nullability.NonNull -> false
                Nullability.NoTransform -> bodyType.nullable
            }
        } else bodyType.nullable
    }

    fun transformType(bodyType: IWrappedBodyType, typesWithinScope: Set<String>, bodyTypeVariables: Map<String, IWrappedBodyType>): String {
        val customTransformer = customTransformers.find { t -> t.predicate(bodyType) }
        return when {
            customTransformer != null -> customTransformer.type
            bodyType.isWildCard -> "any"
            bodyType.name != null && typesWithinScope.contains(bodyType.name as String) -> "${bodyType.name}${transformTypeParameters(bodyType, typesWithinScope, bodyTypeVariables)}"
            bodyType.isReturningTypeVariable -> "${bodyType.name}"
            bodyType.isTypeVariable && bodyTypeVariables.containsKey(bodyType.name) -> "${bodyType.name}${transformTypeParameters(bodyType, typesWithinScope, bodyTypeVariables)}"
            else -> {
                val transformer = if (!bodyType.hasParameters) valueTransformers.find { t -> t.predicate(bodyType) } else null
                transformer?.type
                        ?: transformCollectionType(bodyType, typesWithinScope, bodyTypeVariables)
                        ?: "any /* unknown bodyType */"
            }
        }
    }

    private fun transformCollectionType(bodyType: IWrappedBodyType, typesWithinScope: Set<String>, bodyTypeVariables: Map<String, IWrappedBodyType>): String? {
        when {
            bodyType.isMap && bodyType.hasParameters -> {
                // check if first parameter is string or number.. then we can just use object notation
                val firstParam = bodyType.firstParameterType
                val secondParam = bodyType.secondParameterType
                return when {
                    firstParam nameEquals String::class -> "{ [key: string]: ${transformType(secondParam, typesWithinScope, bodyTypeVariables)} }"
                    numericClasses.any { c -> firstParam nameEquals c } -> "{ [key: number]: ${transformType(secondParam, typesWithinScope, bodyTypeVariables)} }"
                    else -> "Map${transformTypeParameters(bodyType, typesWithinScope, bodyTypeVariables)}"
                }
            }
            bodyType.isMap -> {
                return "Map<any, any>"
            }
            (bodyType.isIterable || bodyType.isArray) && bodyType.hasParameters -> {
                if (bodyType.parameters.size == 1) { // can it be something else?
                    return "Array${transformTypeParameters(bodyType, typesWithinScope, bodyTypeVariables)}"
                }
            }
            bodyType.isIterable -> {
                return "Array<any>"
            }
            bodyType.isArray -> {
                return "any:[]"
            }
            bodyType.isPair && bodyType.hasParameters -> {
                val firstParam = bodyType.firstParameterType
                val secondParam = bodyType.secondParameterType
                return "[${transformType(firstParam, typesWithinScope, bodyTypeVariables)}, ${transformType(secondParam, typesWithinScope, bodyTypeVariables)}]"
            }
            bodyType.isPair -> {
                return "[any, any]"
            }
            bodyType.isSet && bodyType.hasParameters -> {
                return "Set${transformTypeParameters(bodyType, typesWithinScope, bodyTypeVariables)}"
            }
            bodyType.isSet -> {
                return "Set<any>"
            }
        }

        return null
    }

    private fun transformTypeParameters(bodyType: IWrappedBodyType, typesWithinScope: Set<String>, bodyTypeVariables: Map<String, IWrappedBodyType>): String {
        if (bodyType.hasParameters) {
            return "<${bodyType.parameters.values.joinToString(", ") {
                    transformType(it, typesWithinScope, bodyTypeVariables)
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
                TypeTransformer({ r -> r nameEquals String::class}, "string", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Char::class}, "string", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Int::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Long::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Short::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Float::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Double::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Byte::class}, "number", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals Boolean::class}, "boolean", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals IntArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals ShortArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals ByteArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals LongArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals DoubleArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals FloatArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals LongArray::class }, "Array<number>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals CharArray::class }, "Array<string>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals CharArray::class }, "Array<string>", Nullability.NoTransform),
                TypeTransformer({ r -> r nameEquals  Any::class }, "any", Nullability.NoTransform)
        )
    }
}
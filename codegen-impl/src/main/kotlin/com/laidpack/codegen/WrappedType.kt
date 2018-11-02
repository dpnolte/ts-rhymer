package com.laidpack.codegen

import com.laidpack.codegen.moshi.rawType
import com.squareup.kotlinpoet.*
import java.lang.IndexOutOfBoundsException
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.reflect.KClass

enum class CollectionType {
    Map,
    Set,
    Iterable,
    Pair,
    Array,
    None
}

internal class WrappedType private constructor (
        override val typeName: TypeName,
        override val variableElement: VariableElement?,
        override val isPropertyValue: Boolean,
        override val isTypeVariable: Boolean,
        override val isEnumValue: Boolean,
        override val isBound: Boolean,
        override val parameters: Map<String, WrappedType>,
        override val annotationNames: Set<String>,
        private val _bounds: Map<String, WrappedType>
) : IWrappedType {
    override val hasRawType = typeName is ClassName || typeName is ParameterizedTypeName
    override val isInstantiable = hasRawType && !isEnumValue
    override val nullable = typeName.nullable
    override val isWildCard = typeName is WildcardTypeName
    override val rawType: ClassName? =  if (hasRawType) typeName.rawType() else null
    override val hasParameters: Boolean
        get() = parameters.isNotEmpty()
    override var collectionType: CollectionType = CollectionType.None
        private set
    override val canonicalName : String? = if (hasRawType) typeName.rawType().canonicalName else null
    override val name : String?
        get() = resolveName(typeName)
    override var isReturningTypeVariable = false
    override var javaCanonicalName: String? = null
    override val isPrimitiveOrStringType by lazy {when {
        this nameEquals String::class -> true
        this nameEquals Int::class -> true
        this nameEquals Boolean::class -> true
        this nameEquals Float::class -> true
        this nameEquals Double::class -> true
        this nameEquals Long::class -> true
        this nameEquals Char::class -> true
        this nameEquals Short::class -> true
        else -> false
    }}

    override val bounds: Map<String, WrappedType>
        get() {
            if (!isTypeVariable) throw IllegalStateException("Bounds are only available for type variables. Type is $name")
            return _bounds
        }

    override val isMap: Boolean
        get() = collectionType == CollectionType.Map
    override val isIterable: Boolean
        get() = collectionType == CollectionType.Iterable
    override val isSet: Boolean
        get() = collectionType == CollectionType.Set
    override val isPair: Boolean
        get() = collectionType == CollectionType.Pair
    override val isArray: Boolean
        get() = collectionType == CollectionType.Array

    override val firstParameterType: WrappedType
        get() = getParameterTypeAt(0)
    override val secondParameterType: WrappedType
        get() = getParameterTypeAt(1)

    fun getParameterTypeAt(index: Int): WrappedType {
        if (!hasParameters || typeName !is ParameterizedTypeName) throw IllegalStateException("Type $name has no template parameters")
        if (index < 0 || typeName.typeArguments.size < index)  throw IndexOutOfBoundsException("Template parameter index $index is out of bounds")
        val typeArgument = typeName.typeArguments[index]
        val name = resolveName(typeArgument) ?: throw IllegalStateException("Parameter has no name")
        if (!parameters.containsKey(name)) throw IllegalStateException("Parameter with name '$name' is not in parameters map")
        return parameters[name] as WrappedType
    }

    companion object {
        var getSuperTypeNames = { typeMirror: TypeMirror, context: TargetContext  ->
            context.typeUtils.directSupertypes(typeMirror).map { it.asTypeName() }
        }

        var getMirror = {wrappedType: WrappedType, context: TargetContext -> getMirrorDefaultImpl(wrappedType, context)}

        fun resolveName(typeName: TypeName): String? {
            return when (typeName) {
                is WildcardTypeName -> "any"
                is TypeVariableName -> typeName.name
                is ClassName, is ParameterizedTypeName -> typeName.rawType().simpleName
                else -> null
            }
        }

        /**
        wrap type variable,
        add bounds to wrapped type variable
        find current and nested bound types
        for every bound,
        -- check if there are any new declared types
        -- resolve collection types
         **/

        fun resolveGenericClassDeclaration(typeVariableNames: Map<String, TypeVariableName>, context: TargetContext): HashMap<String, WrappedType> {
            val typeVariables = HashMap<String, WrappedType>()
            if (typeVariableNames.isEmpty()) return typeVariables

            typeVariableNames.values.forEach { typeVariableName ->
                val type = wrapTypeVariable(typeVariableName)
                for (boundType in type.bounds.values) {
                    this.performActionsOnTypeAndItsNestedTypes(boundType, context) { foundType, c ->
                        resolveCollectionType(foundType, c)
                        addTypeToScopeIfNewDeclaredType(foundType, c)
                    }
                }
                typeVariables[type.name!!] = type
            }

            return typeVariables
        }

        /**
        wrap property type
        check if value is a declared class type variable
        find nested type variables..
        wrap nested types
        check if there are any new declared types
        resolve collection types
         **/

        fun resolvePropertyType(typeName: TypeName, variableElement: VariableElement?, typeVariables: Map<String, WrappedType>, context: TargetContext): WrappedType {
            val type = wrapPropertyType(typeName, variableElement)
            if (typeVariables.containsKey(type.name)) {
                type.isReturningTypeVariable = true
            }
            this.performActionsOnTypeAndItsNestedTypes(type, context) { foundType, c ->
                resolveCollectionType(foundType, c)
                addTypeToScopeIfNewDeclaredType(foundType, c)
            }
            return type
        }

        fun resolveEnumValueType(typeName: TypeName): WrappedType {
            return wrapEnumValueType(typeName)
        }

        private fun get(
                typeName: TypeName,
                variableElement: VariableElement?,
                isPropertyValue: Boolean,
                isTypeVariable: Boolean,
                isEnumValue: Boolean,
                isBound: Boolean
        ): WrappedType {
            val wrappedType = WrappedType(
                    typeName,
                    variableElement,
                    isPropertyValue,
                    isTypeVariable,
                    isEnumValue,
                    isBound,
                    getParameterTypes(typeName),
                    getAnnotationNames(variableElement),
                    getBounds(isTypeVariable, typeName)
            )
            injectJavaMirroredCanonicalName(wrappedType)
            return wrappedType
        }

        private fun getParameterTypes(typeName: TypeName): Map<String, WrappedType> {
            val parameters = mutableMapOf<String, WrappedType>()
            if (typeName is ParameterizedTypeName) {
                typeName.typeArguments.forEach {
                    val name = WrappedType.resolveName(it)
                    if (name != null && !parameters.containsKey(name)) {
                        parameters[name] = wrapTypeVariable(it)
                    }
                }
            }
            return parameters
        }

        private fun getAnnotationNames(variableElement: VariableElement?): Set<String> {
            val annotationNames = mutableSetOf<String>()
            if (variableElement != null) {
                annotationNames.addAll(
                        variableElement.annotationMirrors.map { annotationMirror ->
                            annotationMirror.annotationType.asTypeName().toString()
                        }
                )
            }
            return annotationNames
        }

        private fun getBounds(isTypeVariable: Boolean, typeName: TypeName): Map<String, WrappedType> {
            val bounds = mutableMapOf<String, WrappedType>()
            if (isTypeVariable && typeName is TypeVariableName) {
                typeName.bounds.forEach {
                    val name = WrappedType.resolveName(it)
                    if (name != null) {
                        val boundType = wrapBoundType(it)
                        bounds[name] = boundType
                    }
                }
            }
            return bounds
        }

        private fun injectJavaMirroredCanonicalName(type: WrappedType) {
            if (type.variableElement != null ) {
                val typeMirror = type.variableElement.asType()
                injectJavaMirroredCanonicalName(type, typeMirror)
            }
        }

        private fun injectJavaMirroredCanonicalName(type: WrappedType, typeMirror: TypeMirror) {
            val javaTypeName = typeMirror.asTypeName()
            if (javaTypeName is ClassName || javaTypeName is ParameterizedTypeName) {
                type.javaCanonicalName = javaTypeName.rawType().canonicalName
            }
            if (type.typeName is ParameterizedTypeName && typeMirror is DeclaredType) {
                val max = typeMirror.typeArguments.size -1
                for (i in 0..max) {
                    val parameterTypeMirror= typeMirror.typeArguments[i]
                    val parameterWrappedType = type.getParameterTypeAt(i)
                    injectJavaMirroredCanonicalName(parameterWrappedType, parameterTypeMirror)
                }
            }
        }

        private fun addTypeToScopeIfNewDeclaredType(type: WrappedType, context: TargetContext) {
            if (!type.isPrimitiveOrStringType && type.isInstantiable && type.collectionType == CollectionType.None
                    && !context.typesWithinScope.contains(type.name) && !context.typesToBeAddedToScope.containsKey(type.name)) {
                val typeElement = context.elementUtils.getTypeElement(type.canonicalName)
                if (typeElement != null && typeElement.asType() is DeclaredType) {
                    context.typesToBeAddedToScope[type.name!!] = typeElement
                }
            }

        }

        private fun resolveCollectionType(type: WrappedType, context: TargetContext) {
            if (type.isPrimitiveOrStringType) return

            val simpleMapping = when {
                type nameEquals Pair::class -> CollectionType.Pair
                type nameEquals List::class -> CollectionType.Iterable
                type nameEquals Set::class -> CollectionType.Set
                type nameEquals Map::class -> CollectionType.Map
                type nameEquals HashMap::class -> CollectionType.Map
                else -> null
            }

            if (simpleMapping != null) {
                type.collectionType = simpleMapping
                return
            }

            val typeMirror = getMirror(type, context) ?: return
            val kind = typeMirror.kind
            if (kind == TypeKind.ARRAY) {
                type.collectionType = CollectionType.Array
                return
            }
            // add recursive check?
            loop@ for (superTypeName in getSuperTypeNames(typeMirror, context)) {
                var exit = true
                when {
                    superTypeName nameEquals Map::class -> type.collectionType = CollectionType.Map
                    superTypeName nameEquals Set::class -> type.collectionType = CollectionType.Set
                    superTypeName nameEquals Collection::class -> type.collectionType = CollectionType.Iterable
                    else -> exit = false
                }
                if (exit) break@loop
            }
        }

        fun performActionsOnTypeAndItsNestedTypes(type: WrappedType, context: TargetContext, action: (foundType: WrappedType, context: TargetContext) -> Any) {
            action(type, context)
            if (type.hasParameters) {
                type.parameters.values.forEach {
                    performActionsOnTypeAndItsNestedTypes(it, context, action)
                }
            }
        }

        private fun getMirrorDefaultImpl(wrappedType: WrappedType, context: TargetContext, preferJavaTypeMirror: Boolean = true): TypeMirror? {
            var typeMirror : TypeMirror? = null
            if (preferJavaTypeMirror) {
                if (wrappedType.variableElement != null) {
                    typeMirror = wrappedType.variableElement.asType()
                    return typeMirror
                }
                // try to resovle type from root.. we prefer java typess and the root type contains the information
                if (typeMirror == null && wrappedType.javaCanonicalName != null) {
                    val typeElement = context.elementUtils.getTypeElement(wrappedType.javaCanonicalName)
                    typeMirror = typeElement?.asType()

                }
            }
            if (typeMirror == null && wrappedType.canonicalName != null) {
                val typeElement = context.elementUtils.getTypeElement(wrappedType.canonicalName)
                typeMirror = typeElement?.asType()
            }
            return typeMirror
        }

        private fun wrapPropertyType(typeName: TypeName, variableElement: VariableElement?): WrappedType {
            val type = WrappedType.get(typeName, variableElement,true, false, false, false)
            return type
        }
        private fun wrapTypeVariable(typeName: TypeName): WrappedType {
            val type =  WrappedType.get(typeName, null,false,true, false, false)
            return type
        }
        private fun wrapEnumValueType(typeName: TypeName): WrappedType {
            return WrappedType.get(typeName, null,false,false, true, false)
        }
        private fun wrapBoundType(typeName: TypeName): WrappedType {
            return WrappedType.get(typeName, null,false,false, false, true)
        }
    }
}


internal infix fun WrappedType.nameEquals(classType: KClass<*>): Boolean {
    return this.canonicalName != null &&
            (this.canonicalName == classType.qualifiedName || this.canonicalName == classType.java.canonicalName)
}
internal infix fun IWrappedType.nameEquals(classType: KClass<*>): Boolean {
    return this.canonicalName != null &&
            (this.canonicalName == classType.qualifiedName || this.canonicalName == classType.java.canonicalName)
}
internal infix fun TypeName.nameEquals(classType: KClass<*>): Boolean {
    return (this is ClassName || this is ParameterizedTypeName) &&
            (this.rawType().canonicalName == classType.qualifiedName || this.rawType().canonicalName == classType.java.canonicalName)
}

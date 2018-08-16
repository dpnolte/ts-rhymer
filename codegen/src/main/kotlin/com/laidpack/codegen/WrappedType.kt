package com.laidpack.codegen

import com.laidpack.codegen.moshi.TargetType
import com.laidpack.codegen.moshi.rawType
import com.squareup.kotlinpoet.*
import java.lang.IndexOutOfBoundsException
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.reflect.KClass


enum class CollectionType {
    Map,
    Set,
    Iterable,
    Pair,
    Array,
    None
}


internal open class WrappedType private constructor (
        val typeName: TypeName,
        val variableElement: VariableElement?,
        val isPropertyValue: Boolean,
        val isTypeVariable: Boolean,
        val isEnumValue: Boolean,
        val isBound: Boolean
) {
    private val hasRawType = typeName is ClassName || typeName is ParameterizedTypeName
    val isInstantiable = hasRawType && !isEnumValue
    val nullable = typeName.nullable
    val isWildCard = typeName is WildcardTypeName
    val rawType: ClassName? =  if (hasRawType) typeName.rawType() else null
    val parameters: HashMap<String, WrappedType> = HashMap()
    val hasParameters: Boolean
        get() = parameters.isNotEmpty()
    var collectionType: CollectionType = CollectionType.None
        private set
    val canonicalName : String? = if (hasRawType) typeName.rawType().canonicalName else null
    val name : String?
        get() = resolveName(typeName)
    var isReturningTypeVariable = false
    var unappliedCannonicalName: String? = null
    val isPrimitiveType = when {
        this nameEquals String::class -> true // not really a primitive but treat it as such
        this nameEquals Int::class -> true
        this nameEquals Boolean::class -> true
        this nameEquals Float::class -> true
        this nameEquals Double::class -> true
        this nameEquals Long::class -> true
        this nameEquals Char::class -> true
        this nameEquals Short::class -> true
        else -> false
    }

    private val _bounds = HashMap<String, WrappedType>()
    val bounds: HashMap<String, WrappedType>
        get() {
            if (!isTypeVariable) throw IllegalStateException("Bounds are only available for type variables. Type is $name")
            return _bounds
        }

    val isMap: Boolean
        get() = collectionType == CollectionType.Map
    val isIterable: Boolean
        get() = collectionType == CollectionType.Iterable
    val isSet: Boolean
        get() = collectionType == CollectionType.Set
    val isPair: Boolean
        get() = collectionType == CollectionType.Pair
    val isArray: Boolean
        get() = collectionType == CollectionType.Array

    init {
        injectParameters()
        injectBounds()
        injectJavaMirrorCanonicalName() // must be done after injecting parameters
    }

    private fun injectParameters() {
        if (typeName !is ParameterizedTypeName) return

        typeName.typeArguments.forEach {
            val name = WrappedType.resolveName(it)
            if (name != null && !parameters.containsKey(name)) {
                parameters[name] = wrapTypeVariable(it)
            }
        }
    }

    private fun injectBounds() {
        if (isTypeVariable && typeName is TypeVariableName) {
            typeName.bounds.forEach {
                val name = WrappedType.resolveName(it)
                if (name != null) {
                    val boundType = wrapBoundType(it)
                    bounds[name] = boundType
                }
            }
        }
    }

    private fun injectJavaMirrorCanonicalName() {
        // besides applied type info, add canonical name as known to javac so that we can identify collection types
        if (variableElement != null ) {
            val javaTypeMirror = variableElement.asType()

            injectJavaMirrorCanonicalName(javaTypeMirror)
        }
    }

    private fun injectJavaMirrorCanonicalName(javaTypeMirror: TypeMirror) {
        val javaTypeName = javaTypeMirror.asTypeName()
        if (javaTypeName is ClassName || javaTypeName is ParameterizedTypeName) {
            this.unappliedCannonicalName = javaTypeName.rawType().canonicalName
        }
        if (typeName is ParameterizedTypeName && javaTypeMirror is DeclaredType) {
            val max = javaTypeMirror.typeArguments.size -1
            for (i in 0..max) {
                val parameterJavaTypeName = javaTypeMirror.typeArguments[i]
                val parameterWrappedType = this.getParameterTypeAt(i)
                parameterWrappedType.injectJavaMirrorCanonicalName(parameterJavaTypeName)
            }
        }
    }

    private fun getParameterTypeAt(index: Int): WrappedType {
            if (!hasParameters || typeName !is ParameterizedTypeName) throw IllegalStateException("Type $name has no template parameters")
            if (index < 0 || typeName.typeArguments.size < index)  throw IndexOutOfBoundsException("Template parameter index $index is out of bounds")
            val typeArgument = typeName.typeArguments[index]
            val name = resolveName(typeArgument) ?: throw IllegalStateException("Parameter has no name")
            if (!parameters.containsKey(name)) throw IllegalStateException("Parameter with name '$name' is not in parameters map")
            return parameters[name] as WrappedType
    }

    val firstParameterType: WrappedType
        get() = getParameterTypeAt(0)
    val secondParameterType: WrappedType
        get() = getParameterTypeAt(1)


    private fun addTypeToScopeIfNewDeclaredType(context: TargetContext) {
        if (!this.isPrimitiveType && this.isInstantiable && this.collectionType == CollectionType.None
                && !context.typesWithinScope.contains(this.name) && !context.typesToBeAddedToScope.containsKey(this.name)) {
            val typeElement = context.elementUtils.getTypeElement(this.canonicalName)
            if (typeElement != null && typeElement.asType() is DeclaredType) {
                context.typesToBeAddedToScope[this.name!!] = typeElement
            }
        }

    }

    private fun resolveCollectionType(context: TargetContext) {
        if (this.isPrimitiveType) return

        val simpleMapping = when {
            this nameEquals Pair::class -> CollectionType.Pair
            this nameEquals List::class -> CollectionType.Iterable
            this nameEquals Set::class -> CollectionType.Set
            this nameEquals Map::class -> CollectionType.Map
            this nameEquals HashMap::class -> CollectionType.Map
            else -> null
        }

        if (simpleMapping != null) {
            this.collectionType = simpleMapping
            return
        }

        val typeMirror = getMirror(this, context) ?: return
        val kind = typeMirror.kind
        if (kind == TypeKind.ARRAY) {
            this.collectionType = CollectionType.Array
            return
        }
        // add recursive check?
        loop@ for (superTypeName in getSuperTypeNames(typeMirror, context)) {
            var exit = true
            when {
                superTypeName nameEquals Map::class -> this.collectionType = CollectionType.Map
                superTypeName nameEquals Set::class -> this.collectionType = CollectionType.Set
                superTypeName nameEquals Collection::class -> this.collectionType = CollectionType.Iterable
                else -> exit = false
            }
            if (exit) break@loop
        }
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
                        foundType.resolveCollectionType(c)
                        foundType.addTypeToScopeIfNewDeclaredType(c)
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
                foundType.resolveCollectionType(c)
                foundType.addTypeToScopeIfNewDeclaredType(c)
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
                isTemplateParameter: Boolean,
                isEnumValue: Boolean,
                isBound: Boolean
        ): WrappedType {
            return WrappedType(typeName, variableElement, isPropertyValue, isTemplateParameter, isEnumValue, isBound)
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
                if (typeMirror == null && wrappedType.unappliedCannonicalName != null) {
                    val typeElement = context.elementUtils.getTypeElement(wrappedType.unappliedCannonicalName)
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
internal infix fun TypeName.nameEquals(classType: KClass<*>): Boolean {
    return (this is ClassName || this is ParameterizedTypeName) &&
            (this.rawType().canonicalName == classType.qualifiedName || this.rawType().canonicalName == classType.java.canonicalName)
}

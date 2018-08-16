package com.laidpack.codegen

import com.laidpack.codegen.moshi.TargetType
import com.laidpack.codegen.moshi.rawType
import com.squareup.kotlinpoet.*
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.LinkedHashSet
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

internal class WrappedTypeTest {
    private val emptyTypeVariablesMap = HashMap<String, WrappedType>()
    private lateinit var mockedElements: Elements
    private lateinit var mockedTypes: Types
    private lateinit var mockedContext: TargetContext
    private var mockedTypeElement = Mockito.mock(TypeElement::class.java)

    @Before
    fun setUp() {
        mockedElements = Mockito.mock(Elements::class.java)
        mockedTypes = Mockito.mock(Types::class.java)
        mockedContext = Mockito.mock(TargetContext::class.java)
        val mockedTypeMirror = Mockito.mock(TypeMirror::class.java)

        Mockito.`when`(mockedTypeElement.asType()).thenReturn(mockedTypeMirror)
        Mockito.`when`(mockedTypeMirror.kind).thenReturn(TypeKind.OTHER)
    }

    @Test
    fun `performActionsOnCurrentAndNestedTypes - given type C'A'List'String, then return C, A, List, and String,`() {
        // given
        val mockedType = Mockito.mock(WrappedType::class.java)
        val mockedSubTemplateType = Mockito.mock(WrappedType::class.java)
        val mockedSubListType = Mockito.mock(WrappedType::class.java)
        val mockedStringType = Mockito.mock(WrappedType::class.java)
        Mockito.`when`(mockedType.name).thenReturn("C")
        Mockito.`when`(mockedType.hasParameters).thenReturn(true)
        Mockito.`when`(mockedType.parameters).thenReturn(hashMapOf("A" to mockedSubTemplateType))

        Mockito.`when`(mockedSubTemplateType.name).thenReturn("A")
        Mockito.`when`(mockedSubTemplateType.hasParameters).thenReturn(true)
        Mockito.`when`(mockedSubTemplateType.parameters).thenReturn(hashMapOf("List" to mockedSubListType))

        Mockito.`when`(mockedSubListType.name).thenReturn("List")
        Mockito.`when`(mockedSubListType.hasParameters).thenReturn(true)
        Mockito.`when`(mockedSubListType.parameters).thenReturn(hashMapOf("String" to mockedStringType))

        Mockito.`when`(mockedStringType.name).thenReturn("String")
        Mockito.`when`(mockedStringType.hasParameters).thenReturn(false)

        val list = mutableListOf<String>()
        val action = {type: WrappedType, context: TargetContext-> list.add(type.name!!)}
        WrappedType.performActionsOnTypeAndItsNestedTypes(mockedType, mockedContext, action)

        list[0] shouldEqual "C"
        list[1] shouldEqual "A"
        list[2] shouldEqual "List"
        list[3] shouldEqual "String"
    }

    @Test
    fun `resolvePropertyType - given type List, return CollectionType'Iterable`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.rawType().canonicalName).thenReturn(List::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(List::class.java.simpleName)

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Iterable
    }

    @Test
    fun `resolvePropertyType - given type MutableList, return CollectionType'Iterable`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.rawType().canonicalName).thenReturn(MutableList::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(MutableList::class.java.simpleName)

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Iterable
    }

    @Test
    fun `resolvePropertyType - given type HashMap, return CollectionType'Map`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.canonicalName).thenReturn(HashMap::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(HashMap::class.java.simpleName)
        Mockito.`when`(mockedElements.getTypeElement(HashMap::class.java.canonicalName)).thenReturn(mockedTypeElement)

        WrappedType.getSuperTypeNames = { a, b ->
            HashMap::class.supertypes.map {
                it.asTypeName()
            }
        }

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Map
    }

    @Test
    fun `resolvePropertyType - given type LinkedHashMap, return CollectionType'Map`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.canonicalName).thenReturn(LinkedHashMap::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(LinkedHashMap::class.java.simpleName)
        Mockito.`when`(mockedElements.getTypeElement(LinkedHashMap::class.java.canonicalName)).thenReturn(mockedTypeElement)

        WrappedType.getSuperTypeNames = { a, b ->
            LinkedHashMap::class.supertypes.map {
                it.asTypeName()
            }
        }

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Map
    }


    @Test
    fun `resolvePropertyType - given type LinkedHashSet, return CollectionType'Set`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.canonicalName).thenReturn(LinkedHashSet::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(LinkedHashSet::class.java.simpleName)
        Mockito.`when`(mockedElements.getTypeElement(LinkedHashSet::class.java.canonicalName)).thenReturn(mockedTypeElement)

        WrappedType.getSuperTypeNames = { a, b ->
            LinkedHashSet::class.supertypes.map {
                it.asTypeName()
            }
        }
        WrappedType.getMirror = { a, b ->
            Mockito.mock(TypeMirror::class.java)
        }

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Set
    }


    @Test
    fun `resolvePropertyType - given type Pair, return CollectionType'Pair`() {
        val mockedTypeName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeName.rawType().canonicalName).thenReturn(Pair::class.java.canonicalName)
        Mockito.`when`(mockedTypeName.rawType().simpleName).thenReturn(Pair::class.java.simpleName)

        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        wrappedType.collectionType shouldEqual CollectionType.Pair
    }


    @Test
    fun `resolvePropertyType - given type HashMap''String'T'', return wrapped type with two params + last var returns type variable`() {
        // assemble
        val mockedtypeArgument1 = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedtypeArgument1.rawType().simpleName).thenReturn(String::class.java.simpleName)
        val mockedTypeArgument2 = Mockito.mock(TypeVariableName::class.java)
        Mockito.`when`(mockedTypeArgument2.name).thenReturn("T")
        val mockedClassName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedClassName.canonicalName).thenReturn(HashMap::class.java.canonicalName)
        Mockito.`when`(mockedClassName.simpleName).thenReturn(HashMap::class.java.simpleName)
        val mockedTypeName = Mockito.mock(ParameterizedTypeName::class.java)
        Mockito.`when`(mockedTypeName.rawType()).thenReturn(mockedClassName)
        Mockito.`when`(mockedTypeName.typeArguments).thenReturn(listOf(mockedtypeArgument1, mockedTypeArgument2))

        Mockito.`when`(mockedElements.getTypeElement(HashMap::class.java.canonicalName)).thenReturn(mockedTypeElement)
        WrappedType.getSuperTypeNames = { a, b ->
            HashMap::class.supertypes.map {
                it.asTypeName()
            }
        }
        WrappedType.getMirror = { a, b ->
            Mockito.mock(TypeMirror::class.java)
        }

        // act
        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        // assert
        wrappedType.collectionType shouldEqual CollectionType.Map
        wrappedType.parameters.size shouldEqual 2
        wrappedType.parameters.containsKey(String::class.java.simpleName) shouldEqual true
        wrappedType.parameters.containsKey("T") shouldEqual true
        wrappedType.parameters["T"]?.isTypeVariable shouldEqual true
    }

    @Test
    fun `resolvePropertyType - given type MutableList''Int'', return CollectionType'Iterable + param = Int`() {
        // assemble
        val mockedTypeArgument1 = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedTypeArgument1.rawType().simpleName).thenReturn(Int::class.java.simpleName)
        val mockedClassName = Mockito.mock(ClassName::class.java)
        Mockito.`when`(mockedClassName.canonicalName).thenReturn(MutableList::class.java.canonicalName)
        Mockito.`when`(mockedClassName.simpleName).thenReturn(MutableList::class.java.simpleName)
        val mockedTypeName = Mockito.mock(ParameterizedTypeName::class.java)
        Mockito.`when`(mockedTypeName.rawType()).thenReturn(mockedClassName)
        Mockito.`when`(mockedTypeName.typeArguments).thenReturn(listOf(mockedTypeArgument1))

        Mockito.`when`(mockedElements.getTypeElement(MutableList::class.java.canonicalName)).thenReturn(mockedTypeElement)
        WrappedType.getSuperTypeNames = { a, b ->
            MutableList::class.supertypes.map {
                it.asTypeName()
            }
        }
        WrappedType.getMirror = { a, b ->
            Mockito.mock(TypeMirror::class.java)
        }

        // act
        val wrappedType = WrappedType.resolvePropertyType (mockedTypeName, null, emptyTypeVariablesMap, mockedContext)

        // assert
        wrappedType.collectionType shouldEqual CollectionType.Iterable
        wrappedType.parameters.size shouldEqual 1
        wrappedType.parameters.containsKey(Int::class.java.simpleName) shouldEqual true
    }

}
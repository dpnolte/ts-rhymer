package com.laidpack.codegen

import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

internal class TypeScriptTypeTransformerTest {
    private lateinit var mockedType : WrappedType

    @Before
    fun setUp() {
        mockedType = Mockito.mock(WrappedType::class.java)
    }
    @Test
    fun `transformType - given type List'string, return Array'string in TS`() {
        val mockedStringType = Mockito.mock(WrappedType::class.java)
        Mockito.`when`(mockedType.isIterable).`it returns`(true)
        Mockito.`when`(mockedType.hasParameters).`it returns`(true)
        Mockito.`when`(mockedType.parameters).`it returns`(mapOf("String" to mockedStringType))
        Mockito.`when`(mockedStringType.hasParameters).`it returns`(false)
        Mockito.`when`(mockedStringType.canonicalName).`it returns`(String::class.java.canonicalName)

        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedType, setOf(), mapOf())
        result `should be equal to` "Array<string>"
    }

    @Test
    fun `transformType - given type HashMap'String'T with declared type var T, return { |string-key| T}`() {
        // Assemble
        Mockito.`when`(mockedType.isMap).`it returns`(true)
        Mockito.`when`(mockedType.hasParameters).`it returns`(true)
        val mockedStringType = Mockito.mock(WrappedType::class.java)
        Mockito.`when`(mockedStringType.canonicalName).thenReturn(String::class.java.canonicalName)
        val mockedTypeVariableType = Mockito.mock(WrappedType::class.java)
        Mockito.`when`(mockedTypeVariableType.isTypeVariable).thenReturn(true)
        Mockito.`when`(mockedTypeVariableType.name).thenReturn("T")
        Mockito.`when`(mockedTypeVariableType.hasParameters).`it returns`(false)

        Mockito.`when`(mockedType.parameters).`it returns`(mapOf(
                "String" to mockedStringType,
                "T" to mockedTypeVariableType
        ))
        `when`(mockedType.firstParameterType).thenReturn(mockedStringType)
        `when`(mockedType.secondParameterType).thenReturn(mockedTypeVariableType)

        // Act
        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedType, setOf(), mapOf("T" to mockedTypeVariableType))

        // Assert
        result shouldEqual "{ [key: string]: T }"

    }

    @Test
    fun `transformType - given type MutableList'Int, return Array'number'`() {
        // Assemble
        Mockito.`when`(mockedType.isIterable).`it returns`(true)
        Mockito.`when`(mockedType.hasParameters).`it returns`(true)
        val mockedIntType = Mockito.mock(WrappedType::class.java)
        Mockito.`when`(mockedIntType.canonicalName).thenReturn(Int::class.java.canonicalName)

        Mockito.`when`(mockedType.parameters).`it returns`(mapOf(
                Int::class.java.simpleName to mockedIntType
        ))
        `when`(mockedType.firstParameterType).thenReturn(mockedIntType)

        // Act
        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedType, setOf(), mapOf())

        // Assert
        result shouldEqual "Array<number>"
    }

    @Test
    fun `transformType - given int annotated with Test, return custom value transformed any`() {
        // Assemble
        Mockito.`when`(mockedType.canonicalName).`it returns`(Int::class.java.canonicalName)
        Mockito.`when`(mockedType.isPrimitiveOrStringType).`it returns`(true)
        Mockito.`when`(mockedType.annotationNames).`it returns`(setOf("Test"))

        // Act
        val customValueTransformer = ValueTypeTransformer({ t -> t.annotationNames.contains("Test")}, "string")
        val transformer = TypeScriptTypeTransformer(listOf(customValueTransformer))
        val result = transformer.transformType(mockedType, setOf(), mapOf())

        // Assert
        result shouldEqual "string"
    }

}
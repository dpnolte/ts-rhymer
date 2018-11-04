package com.laidpack.typescript.codegen

import org.amshove.kluent.`it returns`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

internal class TypeScriptTypeTransformerTest {
    private lateinit var mockedBodyType : WrappedBodyType

    @Before
    fun setUp() {
        mockedBodyType = Mockito.mock(WrappedBodyType::class.java)
    }
    @Test
    fun `transformType - given type List'string, return Array'string in TS`() {
        val mockedStringType = Mockito.mock(WrappedBodyType::class.java)
        Mockito.`when`(mockedBodyType.isIterable).`it returns`(true)
        Mockito.`when`(mockedBodyType.hasParameters).`it returns`(true)
        Mockito.`when`(mockedBodyType.parameters).`it returns`(mapOf("String" to mockedStringType))
        Mockito.`when`(mockedStringType.hasParameters).`it returns`(false)
        Mockito.`when`(mockedStringType.canonicalName).`it returns`(String::class.java.canonicalName)

        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedBodyType, setOf(), mapOf())
        result `should be equal to` "Array<string>"
    }

    @Test
    fun `transformType - given type HashMap'String'T with declared type var T, return { |string-key| T}`() {
        // Assemble
        Mockito.`when`(mockedBodyType.isMap).`it returns`(true)
        Mockito.`when`(mockedBodyType.hasParameters).`it returns`(true)
        val mockedStringType = Mockito.mock(WrappedBodyType::class.java)
        Mockito.`when`(mockedStringType.canonicalName).thenReturn(String::class.java.canonicalName)
        val mockedTypeVariableType = Mockito.mock(WrappedBodyType::class.java)
        Mockito.`when`(mockedTypeVariableType.isTypeVariable).thenReturn(true)
        Mockito.`when`(mockedTypeVariableType.name).thenReturn("T")
        Mockito.`when`(mockedTypeVariableType.hasParameters).`it returns`(false)

        Mockito.`when`(mockedBodyType.parameters).`it returns`(mapOf(
                "String" to mockedStringType,
                "T" to mockedTypeVariableType
        ))
        `when`(mockedBodyType.firstParameterType).thenReturn(mockedStringType)
        `when`(mockedBodyType.secondParameterType).thenReturn(mockedTypeVariableType)

        // Act
        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedBodyType, setOf(), mapOf("T" to mockedTypeVariableType))

        // Assert
        result shouldEqual "{ [key: string]: T }"

    }

    @Test
    fun `transformType - given type MutableList'Int, return Array'number'`() {
        // Assemble
        Mockito.`when`(mockedBodyType.isIterable).`it returns`(true)
        Mockito.`when`(mockedBodyType.hasParameters).`it returns`(true)
        val mockedIntType = Mockito.mock(WrappedBodyType::class.java)
        Mockito.`when`(mockedIntType.canonicalName).thenReturn(Int::class.java.canonicalName)

        Mockito.`when`(mockedBodyType.parameters).`it returns`(mapOf(
                Int::class.java.simpleName to mockedIntType
        ))
        `when`(mockedBodyType.firstParameterType).thenReturn(mockedIntType)

        // Act
        val transformer = TypeScriptTypeTransformer()
        val result = transformer.transformType(mockedBodyType, setOf(), mapOf())

        // Assert
        result shouldEqual "Array<number>"
    }

    @Test
    fun `transformType - given int annotated with Test, return custom value transformed string`() {
        // Assemble
        Mockito.`when`(mockedBodyType.canonicalName).`it returns`(Int::class.java.canonicalName)
        Mockito.`when`(mockedBodyType.isPrimitiveOrStringType).`it returns`(true)
        Mockito.`when`(mockedBodyType.annotationNames).`it returns`(setOf("Test"))

        // Act
        val customValueTransformer = TypeTransformer({ t -> t.annotationNames.contains("Test")}, "string", Nullability.NoTransform)
        val transformer = TypeScriptTypeTransformer(listOf(customValueTransformer))
        val result = transformer.transformType(mockedBodyType, setOf(), mapOf())

        // Assert
        result shouldEqual "string"
    }

    @Test
    fun `transformType - given non-nullable int annotated with Test, return transformed nullability as null`() {
        // Assemble
        Mockito.`when`(mockedBodyType.canonicalName).`it returns`(Int::class.java.canonicalName)
        Mockito.`when`(mockedBodyType.isPrimitiveOrStringType).`it returns`(true)
        Mockito.`when`(mockedBodyType.annotationNames).`it returns`(setOf("Test"))

        // Act
        val customValueTransformer = TypeTransformer({ t -> t.annotationNames.contains("Test")}, "string", Nullability.Null)
        val transformer = TypeScriptTypeTransformer(listOf(customValueTransformer))
        val result = transformer.isNullable(mockedBodyType)

        // Assert
        result shouldEqual true
    }

}
package com.laidpack.typescript.codegen

interface TargetPropertyOrEnumValue {
    val name: String
    val bodyType: IWrappedBodyType

    /** Returns the @Json name of this property, or this property's name if none is provided. */
    fun jsonName(): String
}
package com.laidpack.codegen

import com.squareup.kotlinpoet.TypeName

internal interface TargetPropertyOrEnumValue {
    val name: String
    val type: WrappedType

    /** Returns the @Json name of this property, or this property's name if none is provided. */
    fun jsonName(): String
}
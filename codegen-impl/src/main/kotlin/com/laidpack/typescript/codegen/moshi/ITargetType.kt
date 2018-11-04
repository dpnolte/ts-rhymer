package com.laidpack.typescript.codegen.moshi

import com.laidpack.typescript.codegen.IWrappedBodyType
import com.laidpack.typescript.codegen.TargetPropertyOrEnumValue
import com.squareup.kotlinpoet.ClassName

interface ITargetType {
    val name: ClassName
    val propertiesOrEnumValues: Map<String, TargetPropertyOrEnumValue>
    val typeVariables: Map<String, IWrappedBodyType>
    val isEnum: Boolean
    val superTypes: Set<AppliedType>
}
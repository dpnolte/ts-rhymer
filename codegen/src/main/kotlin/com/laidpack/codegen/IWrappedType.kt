package com.laidpack.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import javax.lang.model.element.VariableElement

interface IWrappedType {
    val typeName: TypeName
    val variableElement: VariableElement?
    val isPropertyValue: Boolean
    val isTypeVariable: Boolean
    val isEnumValue: Boolean
    val isBound: Boolean
    val parameters: Map<String, IWrappedType>
    val annotationNames: Set<String>
    val hasRawType: Boolean
    val isInstantiable: Boolean
    val nullable: Boolean
    val isWildCard: Boolean
    val rawType: ClassName?
    val hasParameters: Boolean
    val collectionType: CollectionType
    val canonicalName : String?
    var javaCanonicalName: String?
    val name : String?
    var isReturningTypeVariable: Boolean
    val isPrimitiveOrStringType: Boolean
    val bounds: Map<String, IWrappedType>
    val isMap: Boolean
    val isIterable: Boolean
    val isSet: Boolean
    val isPair: Boolean
    val isArray: Boolean
    val firstParameterType: IWrappedType
    val secondParameterType: IWrappedType
}
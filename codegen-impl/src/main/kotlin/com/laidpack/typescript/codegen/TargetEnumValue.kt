package com.laidpack.typescript.codegen

import com.squareup.moshi.Json
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import javax.lang.model.element.*

/** A enum value in user code that maps to enum bodyType. */
internal data class TargetEnumValue(
        override val name: String,
        override val bodyType: WrappedBodyType,
        val ordinal: Int,
        val proto: ProtoBuf.EnumEntry,
        private val field: VariableElement?
) : TargetPropertyOrEnumValue {


  private val element get() = field!!

  /** Returns the @Json name of this property, or this property's name if none is provided. */
  override fun jsonName(): String {
    val fieldJsonName = element.jsonName

    return when {
      fieldJsonName != null -> fieldJsonName
      else -> name
    }
  }

  private val Element?.jsonName: String?
    get() {
      if (this == null) return null
      return getAnnotation<Json>(Json::class.java)?.name?.replace("$", "\\$")
    }

  override fun toString() = name

  /** Returns the JsonQualifiers on the field and parameter of this property. */
  /*
  private fun jsonQualifiers(): Set<AnnotationMirror> {
    val elementQualifiers = element.qualifiers

    return when {
      elementQualifiers.isNotEmpty() -> elementQualifiers
      else -> setOf()
    }
  }
  private val Element?.qualifiers: Set<AnnotationMirror>
    get() {
      if (this == null) return setOf()
      return AnnotationMirrors.getAnnotatedAnnotations(this, JsonQualifier::class.java)
    }
      /** Returns the JsonQualifiers on the field and parameter of this property. */
  /*
  private fun jsonQualifiers(): Set<AnnotationMirror> {
    val elementQualifiers = element.qualifiers

    return when {
      elementQualifiers.isNotEmpty() -> elementQualifiers
      else -> setOf()
    }
  }
  private val Element?.qualifiers: Set<AnnotationMirror>
    get() {
      if (this == null) return setOf()
      return AnnotationMirrors.getAnnotatedAnnotations(this, JsonQualifier::class.java)
    }
    */
    */
}

/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.laidpack.codegen.moshi

import com.laidpack.codegen.TargetPropertyOrEnumValue
import com.laidpack.codegen.WrappedType
import com.squareup.moshi.Json
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Property
import javax.lang.model.element.*


/** A property in user code that maps to JSON. */
internal data class TargetProperty(
        override val name: String,
        override val type: WrappedType,
        private val proto: Property,
        private val annotationHolder: ExecutableElement?,
        private val field: VariableElement?,
        private val setter: ExecutableElement?,
        private val getter: ExecutableElement?
) : TargetPropertyOrEnumValue {

  private val element get() = field ?: setter ?: getter!!

  /** Returns the @Json name of this property, or this property's name if none is provided. */
  override fun jsonName(): String {
    val fieldJsonName = element.jsonName
    val annotationHolderJsonName = annotationHolder.jsonName

    return when {
      fieldJsonName != null -> fieldJsonName
      annotationHolderJsonName != null -> annotationHolderJsonName
      else -> name
    }
  }

  private val Element?.jsonName: String?
    get() {
      if (this == null) return null
      return getAnnotation(Json::class.java)?.name?.replace("$", "\\$")
    }

  override fun toString() = name



  /*
  private val isTransient get() = field != null && Modifier.TRANSIENT in field.modifiers

  private val isSettable get() = proto.hasSetter || parameter != null

  private val isVisible: Boolean
    get() {
      return proto.visibility == INTERNAL
          || proto.visibility == PROTECTED
          || proto.visibility == PUBLIC
    }


  /** Returns the JsonQualifiers on the field and parameter of this property. */
  private fun jsonQualifiers(): Set<AnnotationMirror> {
    val elementQualifiers = element.qualifiers
    val annotationHolderQualifiers = annotationHolder.qualifiers
    val parameterQualifiers = parameter?.element.qualifiers

    // TODO(jwilson): union the qualifiers somehow?
    return when {
      elementQualifiers.isNotEmpty() -> elementQualifiers
      annotationHolderQualifiers.isNotEmpty() -> annotationHolderQualifiers
      parameterQualifiers.isNotEmpty() -> parameterQualifiers
      else -> setOf()
    }
  }


  private val Element?.qualifiers: Set<AnnotationMirror>
    get() {
      if (this == null) return setOf()
      return AnnotationMirrors.getAnnotatedAnnotations(this, JsonQualifier::class.java)
    }
    */

}

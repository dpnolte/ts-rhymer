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
package com.laidpack.typescript.codegen.moshi

import com.squareup.kotlinpoet.*
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Types

/**
 * A concrete bodyType like `List<String>` with enough information to know how to resolve its bodyType
 * variables.
 */
class AppliedType private constructor(
  val element: TypeElement,
  val resolver: TypeResolver,
  val className: ClassName,
  private val mirror: DeclaredType
) {
  /** Returns super type. Includes both interface and class supertypes. */
  fun supertypes(
    types: Types,
    result: MutableSet<AppliedType> = mutableSetOf()
  ): Set<AppliedType> {
    //result.add(this)
    for (supertype in types.directSupertypes(mirror)) {
      val supertypeDeclaredType = supertype as DeclaredType
      val supertypeElement = supertypeDeclaredType.asElement() as TypeElement
      val appliedSupertype = AppliedType(
              supertypeElement,
              resolver(supertypeElement, supertypeDeclaredType),
              supertypeElement.asClassName(),
              supertypeDeclaredType
      )
      result.add(appliedSupertype)
      //appliedSupertype.supertypes(types, result)
    }
    return result
  }

  /** Returns a resolver that uses `element` and `mirror` to resolve bodyType parameters. */
  private fun resolver(element: TypeElement, mirror: DeclaredType): TypeResolver {
    return object : TypeResolver() {
      override fun resolveTypeVariable(typeVariable: TypeVariableName): TypeName {
        val index = element.typeParameters.indexOfFirst {
          it.simpleName.toString() == typeVariable.name
        }
        check(index != -1) { "Unexpected bodyType variable $typeVariable in $mirror" }
        val argument = mirror.typeArguments[index]
        return argument.asTypeName()
      }
    }
  }

  override fun toString() = mirror.toString()

  companion object {
    fun get(typeElement: TypeElement): AppliedType {
      return AppliedType(typeElement, TypeResolver(), typeElement.asClassName(), typeElement.asType() as DeclaredType)
    }
  }
}
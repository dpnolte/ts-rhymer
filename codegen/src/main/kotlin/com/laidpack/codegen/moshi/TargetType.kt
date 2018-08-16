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

import com.laidpack.codegen.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import me.eugeniomarletti.kotlin.metadata.KotlinClassMetadata
import me.eugeniomarletti.kotlin.metadata.KotlinMetadata
import me.eugeniomarletti.kotlin.metadata.classKind
import me.eugeniomarletti.kotlin.metadata.getPropertyOrNull
import me.eugeniomarletti.kotlin.metadata.isInnerClass
import me.eugeniomarletti.kotlin.metadata.kotlinMetadata
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Class
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.TypeParameter
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf.Visibility.LOCAL
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.deserialization.NameResolver
import me.eugeniomarletti.kotlin.metadata.shadow.util.capitalizeDecapitalize.decapitalizeAsciiOnly
import me.eugeniomarletti.kotlin.metadata.visibility
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic.Kind.ERROR
import javax.tools.Diagnostic.Kind.WARNING

/** A user type that should be decoded and encoded by generated code. */
internal data class TargetType(
        val proto: Class,
        val element: TypeElement,
        val name: ClassName,
        val propertiesOrEnumValues: Map<String, TargetPropertyOrEnumValue>,
        val typeVariables: Map<String, WrappedType>,
        val superTypes: Set<AppliedType>,
        val isTypeScriptAnnotated: Boolean
) {


  companion object {
    private val OBJECT_CLASS = ClassName("java.lang", "Object")

    /** Returns a target type for `element`, or null if it cannot be used with code gen. */
    fun get(element: Element, context: TargetContext): TargetType? {
      val typeMetadata: KotlinMetadata? = element.kotlinMetadata
      if (element !is TypeElement || typeMetadata !is KotlinClassMetadata) {
        if (context.failOnError)
          context.messager.printMessage(ERROR, "@TypeScript can't be applied to $element: must be a Kotlin class", element)
        return null
      }

      val proto = typeMetadata.data.classProto
      if (proto.classKind == Class.Kind.ENUM_CLASS) {
        return getEnumTargetType(element, proto, typeMetadata, context)
      }

      return getDeclaredTargetType(element, proto, typeMetadata, context)
    }

    private fun getEnumTargetType(element: TypeElement, proto: Class, typeMetadata: KotlinClassMetadata, context: TargetContext): TargetType? {

      val enumValues = declaredEnumValues(element, proto, typeMetadata)

      val typeName = element.asType().asTypeName()
      val name = when (typeName) {
        is ClassName -> typeName
        is ParameterizedTypeName -> typeName.rawType
        else -> throw IllegalStateException("unexpected TypeName: ${typeName::class}")
      }

      return TargetType(proto, element, name, enumValues, mapOf(), setOf(), context.targetingTypscriptAnnotatedType)
    }

    private fun getDeclaredTargetType(element: TypeElement, proto: Class, typeMetadata: KotlinClassMetadata, context: TargetContext): TargetType? {
      when {
        proto.classKind != Class.Kind.CLASS -> {
          context.messager.printMessage( if (context.failOnError) ERROR else WARNING, "@TypeScript can't be applied to $element: must be a Kotlin class", element)
          return null
        }
        proto.isInnerClass -> {
          context.messager.printMessage( if (context.failOnError) ERROR else WARNING, "@TypeScript can't be applied to $element: must not be an inner class", element)
          return null
        }
        proto.visibility == LOCAL -> {
          context.messager.printMessage( if (context.failOnError) ERROR else WARNING, "@TypeScript can't be applied to $element: must not be local", element)
          return null
        }
      }

      val type = element.asType()
      val typeName = type.asTypeName()

      if (typeName nameEquals Pair::class) {
        // don't add a target type for Pair<A,B> that needs to be defined, use Typescript's [A,B] notation
        return null
      }

      val typeVariableNames = genericTypeNames(proto, typeMetadata.data.nameResolver)
      val typeVariables = WrappedType.resolveGenericClassDeclaration(typeVariableNames, context)
      val appliedType = AppliedType.get(element)

      val properties = declaredProperties(element, appliedType.resolver, typeVariables, context)
      val selectedSuperTypes = resolveSuperTypes(appliedType, context) ?: return null

      val name = when (typeName) {
        is ClassName -> typeName
        is ParameterizedTypeName -> typeName.rawType
        else -> throw IllegalStateException("unexpected TypeName: ${typeName::class}")
      }
      return TargetType(proto, element, name, properties, typeVariables, selectedSuperTypes, context.targetingTypscriptAnnotatedType)
    }

      private fun resolveSuperTypes(appliedType: AppliedType, context: TargetContext): Set<AppliedType>? {
          val selectedSuperTypes = mutableSetOf<AppliedType>()
          for (supertype in appliedType.supertypes(context.typeUtils)) {
              if (supertype.element.asClassName() == OBJECT_CLASS) {
                  continue // Don't load propertiesOrEnumValues for java.lang.Object.
              }
              if (supertype.element.kind != ElementKind.CLASS) {
                  continue // Don't load propertiesOrEnumValues for interface types.
              }
              if (supertype.element.kotlinMetadata == null) {
                  context.messager.printMessage(ERROR,
                          "@TypeScript can't be applied to ${appliedType.element.simpleName}: supertype $supertype is not a Kotlin type",
                          appliedType.element)
                  return null
              }
              if (supertype.element.asClassName() != appliedType.element.asClassName()) {
                  selectedSuperTypes.add(supertype)
                  context.typesToBeAddedToScope[supertype.element.simpleName.toString()] = supertype.element
              }
          }
          return selectedSuperTypes
      }

    /** Returns the propertiesOrEnumValues declared by `typeElement`. */
    private fun declaredProperties(
            typeElement: TypeElement,
            typeResolver: TypeResolver,
            typeVariables: Map<String, WrappedType>,
            context: TargetContext
    ): Map<String, TargetProperty> {
      val typeMetadata: KotlinClassMetadata = typeElement.kotlinMetadata as KotlinClassMetadata
      val nameResolver = typeMetadata.data.nameResolver
      val classProto = typeMetadata.data.classProto

      val annotationHolders = mutableMapOf<String, ExecutableElement>()
      val fields = mutableMapOf<String, VariableElement>()
      val setters = mutableMapOf<String, ExecutableElement>()
      val getters = mutableMapOf<String, ExecutableElement>()
      for (element in typeElement.enclosedElements) {
        if (element is VariableElement) {
          fields[element.name] = element
        } else if (element is ExecutableElement) {
          when {
            element.name.startsWith("get") -> {
              val name = element.name.substring("get".length).decapitalizeAsciiOnly()
              getters[name] = element
            }
            element.name.startsWith("is") -> {
              val name = element.name.substring("is".length).decapitalizeAsciiOnly()
              getters[name] = element
            }
            element.name.startsWith("set") -> {
              val name = element.name.substring("set".length).decapitalizeAsciiOnly()
              setters[name] = element
            }
          }

          val propertyProto = typeMetadata.data.getPropertyOrNull(element)
          if (propertyProto != null) {
            val name = nameResolver.getString(propertyProto.name)
            annotationHolders[name] = element
          }
        }
      }

      val result = mutableMapOf<String, TargetProperty>()
      for (property in classProto.propertyList) {
        val name = nameResolver.getString(property.name)
        val typeName = typeResolver.resolve(property.returnType.asTypeName(
                nameResolver, classProto::getTypeParameter, false
        ))

        val wrappedType = WrappedType.resolvePropertyType(typeName, fields[name], typeVariables, context)
        result[name] = TargetProperty(
                name, wrappedType, property,
                annotationHolders[name], fields[name], setters[name], getters[name]
        )

      }

      return result
    }

    /** Returns the propertiesOrEnumValues declared by `typeElement`. */
    private fun declaredEnumValues(
            typeElement: TypeElement,
            classProto: Class,
            typeMetadata: KotlinClassMetadata
    ): Map<String, TargetEnumValue> {
      val nameResolver = typeMetadata.data.nameResolver
      val fields = mutableMapOf<String, VariableElement>()
      for (element in typeElement.enclosedElements) {
        if (element is VariableElement) {
          fields[element.name] = element
        }
      }

      val result = mutableMapOf<String, TargetEnumValue>()
      var ordinal = 0
      for (enumEntry in classProto.enumEntryList) {
        val name = nameResolver.getString(enumEntry.name)
          val wrappedType = WrappedType.resolveEnumValueType(typeElement.asType().asTypeName())
        result[name] = TargetEnumValue(
                name,
                wrappedType, // enum value returns enum class (e.g., TestEnum.One returns --> TestEnum with value 1 in class enum TestEnum (val value; Int) { One(1), Two(2) }
                ordinal,
                enumEntry,
                fields[name]
        )
        ordinal += 1
      }

      return result
    }

    private val Element.name get() = simpleName.toString()

    private fun genericTypeNames(proto: Class, nameResolver: NameResolver): Map<String, TypeVariableName> {
      return proto.typeParameterList.map {
        val possibleBounds = it.upperBoundList
            .map { it.asTypeName(nameResolver, proto::getTypeParameter, false) }
        val typeVar = if (possibleBounds.isEmpty()) {
          TypeVariableName(
              name = nameResolver.getString(it.name),
              variance = it.varianceModifier)
        } else {
        TypeVariableName(
            name = nameResolver.getString(it.name),
            bounds = *possibleBounds.toTypedArray(),
            variance = it.varianceModifier)
        }
        return@map typeVar.reified(it.reified)
      }.associateBy({ it.name }, { it })
    }

    private val TypeParameter.varianceModifier: KModifier?
      get() {
        return variance.asKModifier().let {
          // We don't redeclare out variance here
          if (it == KModifier.OUT) {
            null
          } else {
            it
          }
        }
      }

  }
}

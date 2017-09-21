/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Types
import com.intellij.debugger.streams.trace.impl.handler.type.*
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Contract

/**
 * @author Vitaliy.Bibaev
 */
object JavaTypes : Types {
  override val ANY: GenericType = ClassTypeImpl("java.lang.Object", "new java.lang.Object()")

  override val INT: GenericType = GenericTypeImpl("int", "java.lang.Integer", "0")

  override val BOOLEAN: GenericType = GenericTypeImpl("boolean", "java.lang.Boolean", "false")
  override val DOUBLE: GenericType = GenericTypeImpl("double", "java.lang.Double", "0.")
  override val EXCEPTION: GenericType = ClassTypeImpl("java.lang.Throwable")
  override val VOID: GenericType = GenericTypeImpl("void", "java.lang.Void", "null")

  override val TIME: GenericType = ClassTypeImpl("java.util.concurrent.atomic.AtomicInteger",
                                                 "new java.util.concurrent.atomic.AtomicInteger()")
  override val STRING: GenericType = ClassTypeImpl("java.lang.String", "\"\"")
  override val LONG: GenericType = GenericTypeImpl("long", "java.lang.Long", "0L")

  override fun array(elementType: GenericType): ArrayType =
    ArrayTypeImpl(elementType, { "$it[]" }, "new ${elementType.variableTypeName}[] {}")

  override fun map(keyType: GenericType, valueType: GenericType): MapType =
    MapTypeImpl(keyType, valueType, { keys, values -> "java.util.Map<$keys, $values>" }, "new java.util.HashMap<>()")

  override fun linkedMap(keyType: GenericType, valueType: GenericType): MapType =
    MapTypeImpl(keyType, valueType, { keys, values -> "java.util.Map<$keys, $values>" }, "new java.util.LinkedHashMap<>()")

  override fun list(elementsType: GenericType): ListType =
    ListTypeImpl(elementsType, { "java.util.List<$it>" }, "new java.util.ArrayList<>()")

  override fun nullable(typeSelector: Types.() -> GenericType): GenericType = this.typeSelector()

  private val optional: GenericType = ClassTypeImpl("java.util.Optional")
  private val optionalInt: GenericType = ClassTypeImpl("java.util.OptionalInt")
  private val optionalLong: GenericType = ClassTypeImpl("java.util.OptionalLong")
  private val optionalDouble: GenericType = ClassTypeImpl("java.util.OptionalDouble")

  private val OPTIONAL_TYPES = StreamEx.of(optional, optionalInt, optionalLong, optionalDouble).toSet()

  fun fromStreamPsiType(streamPsiType: PsiType): GenericType {
    return when {
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM) -> INT
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM) -> LONG
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM) -> DOUBLE
      PsiType.VOID == streamPsiType -> VOID
      else -> ANY
    }
  }

  fun fromPsiClass(psiClass: PsiClass): GenericType {
    return when {
      InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM) -> INT
      InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM) -> LONG
      InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM) -> DOUBLE
      else -> ANY
    }
  }

  fun fromPsiType(type: PsiType): GenericType {
    return when (type) {
      PsiType.VOID -> VOID
      PsiType.INT -> INT
      PsiType.DOUBLE -> DOUBLE
      PsiType.LONG -> LONG
      PsiType.BOOLEAN -> BOOLEAN
      else -> ClassTypeImpl(TypeConversionUtil.erasure(type).canonicalText)
    }
  }

  @Contract(pure = true)
  private fun isOptional(type: GenericType): Boolean {
    return OPTIONAL_TYPES.contains(type)
  }

  fun unwrapOptional(type: GenericType): GenericType {
    assert(isOptional(type))

    return when (type) {
      optionalInt -> INT
      optionalLong -> LONG
      optionalDouble -> DOUBLE
      else -> ANY
    }
  }
}
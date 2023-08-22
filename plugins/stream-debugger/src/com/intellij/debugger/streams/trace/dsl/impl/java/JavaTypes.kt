// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Types
import com.intellij.debugger.streams.trace.impl.handler.type.*
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Contract

/**
 * @author Vitaliy.Bibaev
 */
object JavaTypes : Types {
  override val ANY: GenericType = ClassTypeImpl(CommonClassNames.JAVA_LANG_OBJECT, "new java.lang.Object()")

  override val INT: GenericType = GenericTypeImpl("int", CommonClassNames.JAVA_LANG_INTEGER, "0")

  override val BOOLEAN: GenericType = GenericTypeImpl("boolean", CommonClassNames.JAVA_LANG_BOOLEAN, "false")
  override val DOUBLE: GenericType = GenericTypeImpl("double", CommonClassNames.JAVA_LANG_DOUBLE, "0.")
  override val EXCEPTION: GenericType = ClassTypeImpl(CommonClassNames.JAVA_LANG_THROWABLE)
  override val VOID: GenericType = GenericTypeImpl("void", CommonClassNames.JAVA_LANG_VOID, "null")

  override val TIME: GenericType = ClassTypeImpl("java.util.concurrent.atomic.AtomicInteger",
                                                 "new java.util.concurrent.atomic.AtomicInteger()")
  override val STRING: GenericType = ClassTypeImpl(CommonClassNames.JAVA_LANG_STRING, "\"\"")
  override val LONG: GenericType = GenericTypeImpl("long", CommonClassNames.JAVA_LANG_LONG, "0L")

  override fun array(elementType: GenericType): ArrayType =
    ArrayTypeImpl(elementType, { "$it[]" }, { "new ${elementType.variableTypeName}[$it]" })

  override fun map(keyType: GenericType, valueType: GenericType): MapType =
    MapTypeImpl(keyType, valueType, { keys, values -> "java.util.Map<$keys, $values>" }, "new java.util.HashMap<>()")

  override fun linkedMap(keyType: GenericType, valueType: GenericType): MapType =
    MapTypeImpl(keyType, valueType, { keys, values -> "java.util.Map<$keys, $values>" }, "new java.util.LinkedHashMap<>()")

  override fun list(elementsType: GenericType): ListType =
    ListTypeImpl(elementsType, { "java.util.List<$it>" }, "new java.util.ArrayList<>()")

  override fun nullable(typeSelector: Types.() -> GenericType): GenericType = this.typeSelector()

  private val optional: GenericType = ClassTypeImpl(CommonClassNames.JAVA_UTIL_OPTIONAL)
  private val optionalInt: GenericType = ClassTypeImpl("java.util.OptionalInt")
  private val optionalLong: GenericType = ClassTypeImpl("java.util.OptionalLong")
  private val optionalDouble: GenericType = ClassTypeImpl("java.util.OptionalDouble")

  private val OPTIONAL_TYPES = StreamEx.of(optional, optionalInt, optionalLong, optionalDouble).toSet()

  fun fromStreamPsiType(streamPsiType: PsiType): GenericType {
    return when {
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM) -> INT
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM) -> LONG
      InheritanceUtil.isInheritor(streamPsiType, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM) -> DOUBLE
      PsiTypes.voidType() == streamPsiType -> VOID
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
      PsiTypes.voidType() -> VOID
      PsiTypes.intType() -> INT
      PsiTypes.doubleType() -> DOUBLE
      PsiTypes.longType() -> LONG
      PsiTypes.booleanType() -> BOOLEAN
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
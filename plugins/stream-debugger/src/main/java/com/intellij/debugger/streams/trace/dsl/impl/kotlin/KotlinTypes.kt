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
package com.intellij.debugger.streams.trace.dsl.impl.kotlin

import com.intellij.debugger.streams.trace.dsl.Types
import com.intellij.debugger.streams.trace.impl.handler.type.*

/**
 * @author Vitaliy.Bibaev
 */
class KotlinTypes private constructor() : Types by KotlinTypes {
  companion object : Types {
    override val anyType: GenericType = ClassTypeImpl("kotlin.Any", "kotlin.Any()")
    override val integerType: GenericType = ClassTypeImpl("kotlin.Int", "0")
    override val longType: GenericType = ClassTypeImpl("kotlin.Long", "0L")
    override val booleanType: GenericType = ClassTypeImpl("kotlin.Boolean", "false")
    override val doubleType: GenericType = ClassTypeImpl("kotlin.Double", "0.")
    override val stringType: GenericType = ClassTypeImpl("kotlin.String", "\"\"")
    override val basicExceptionType: GenericType = ClassTypeImpl("kotlin.Throwable", "kotlin.Throwable()")
    override val voidType: GenericType = ClassTypeImpl("kotlin.Unit", "Unit")

    override val timeVariableType: GenericType = ClassTypeImpl("java.util.concurrent.atomic.AtomicInteger",
                                                               "new java.util.concurrent.atomic.AtomicInteger()")

    override fun list(elementsType: GenericType): ListType =
      ListTypeImpl(elementsType, { "kotlin.collections.MutableList<$it>" }, "kotlin.collections.mutableListOf()")

    override fun array(elementType: GenericType): ArrayType =
      ArrayTypeImpl(elementType, { "kotlin.Array<$it?>" }, "kotlin.arrayOfNulls(1)")

    override fun map(keyType: GenericType, valueType: GenericType): MapType =
      MapTypeImpl(keyType, valueType,
                  { keys, values -> "kotlin.collections.MutableMap<$keys, $values>" },
                  "kotlin.collections.mutableMapOf()")

    override fun linkedMap(keyType: GenericType, valueType: GenericType): MapType =
      MapTypeImpl(keyType, valueType,
                  { keys, values -> "kotlin.collections.MutableMap<$keys, $values>" },
                  "kotlin.collections.linkedMapOf()")
  }
}
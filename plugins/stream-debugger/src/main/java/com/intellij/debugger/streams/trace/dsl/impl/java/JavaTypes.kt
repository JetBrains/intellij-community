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
import com.intellij.debugger.streams.trace.impl.handler.type.ClassTypeImpl
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.impl.handler.type.GenericTypeImpl
import com.intellij.psi.CommonClassNames

/**
 * @author Vitaliy.Bibaev
 */
class JavaTypes() : Types {
  companion object {

    val BOOLEAN: GenericType = GenericTypeImpl("boolean", "java.lang.Boolean", "false")
    val INT: GenericType = GenericTypeImpl("int", "java.lang.Integer", "0")
    val DOUBLE: GenericType = GenericTypeImpl("double", "java.lang.Double", "0.")
    val LONG: GenericType = GenericTypeImpl("long", "java.lang.Long", "0L")
    val OBJECT: GenericType = ClassTypeImpl("java.lang.Object")
    val VOID: GenericType = GenericTypeImpl("void", "java.lang.Void", "null")
    val OPTIONAL: GenericType = ClassTypeImpl(CommonClassNames.JAVA_UTIL_OPTIONAL)

    val OPTIONAL_INT: GenericType = ClassTypeImpl("java.util.OptionalInt")
    val OPTIONAL_LONG: GenericType = ClassTypeImpl("java.util.OptionalLong")
    val OPTIONAL_DOUBLE: GenericType = ClassTypeImpl("java.util.OptionalDouble")
    val THROWABLE: GenericType = ClassTypeImpl("java.lang.Throwable")
    val STRING: GenericType = ClassTypeImpl("java.lang.String")
  }

  override val anyType: GenericType = OBJECT

  override val integerType: GenericType = INT
  override val booleanType: GenericType = BOOLEAN
  override val doubleType: GenericType = DOUBLE
  override val basicExceptionType: GenericType = THROWABLE
  override val timeVariableType: GenericType = ClassTypeImpl("java.util.concurrent.atomic.AtomicInteger",
                                                             "new java.util.concurrent.atomic.AtomicInteger()")
  override val stringType: GenericType = STRING
  override val longType: GenericType = LONG

  override fun list(elementsType: GenericType): GenericType = ClassTypeImpl("java.util.List<${elementsType.genericTypeName}>")

  override fun map(keyType: GenericType, valueType: GenericType): GenericType =
    ClassTypeImpl("java.util.Map<${keyType.genericTypeName}, ${valueType.genericTypeName}>")
}
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
package com.intellij.debugger.streams.trace.impl.handler.type

import com.intellij.psi.CommonClassNames

/**
 * @author Vitaliy.Bibaev
 */
interface GenericType {
  val variableTypeName: String

  val genericTypeName: String

  val defaultValue: String

  interface CompositeType : GenericType {
    val elementType: GenericType
  }

  companion object {
    val BOOLEAN: GenericType = GenericTypeImpl("boolean", "java.lang.Boolean", "false")
    val INT: GenericType = GenericTypeImpl("int", "java.lang.Integer", "0")
    val DOUBLE: GenericType = GenericTypeImpl("double", "java.lang.Double", "0.")
    val LONG: GenericType = GenericTypeImpl("long", "java.lang.Long", "0L")

    val OBJECT: GenericType = ClassTypeImpl("java.lang.Object", "new java.lang.Object()")
    val VOID: GenericType = GenericTypeImpl("void", "java.lang.Void", "null")
    val OPTIONAL: GenericType = ClassTypeImpl(CommonClassNames.JAVA_UTIL_OPTIONAL)
    val OPTIONAL_INT: GenericType = ClassTypeImpl("java.util.OptionalInt")

    val OPTIONAL_LONG: GenericType = ClassTypeImpl("java.util.OptionalLong")

    val OPTIONAL_DOUBLE: GenericType = ClassTypeImpl("java.util.OptionalDouble")
  }
}

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
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.impl.handler.type.ArrayType
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.impl.handler.type.ListType
import com.intellij.debugger.streams.trace.impl.handler.type.MapType

/**
 * @author Vitaliy.Bibaev
 */
interface Types {
  val ANY: GenericType
  val INT: GenericType
  val LONG: GenericType
  val BOOLEAN: GenericType
  val DOUBLE: GenericType
  val STRING: GenericType
  val EXCEPTION: GenericType
  val VOID: GenericType

  val TIME: GenericType

  fun array(elementType: GenericType): ArrayType
  fun list(elementsType: GenericType): ListType
  fun map(keyType: GenericType, valueType: GenericType): MapType
  fun linkedMap(keyType: GenericType, valueType: GenericType): MapType
}
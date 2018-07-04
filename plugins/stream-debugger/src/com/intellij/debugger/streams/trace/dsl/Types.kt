// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.impl.handler.type.ArrayType
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.trace.impl.handler.type.ListType
import com.intellij.debugger.streams.trace.impl.handler.type.MapType

/**
 * @author Vitaliy.Bibaev
 */
@Suppress("PropertyName")
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

  fun nullable(typeSelector: Types.() -> GenericType): GenericType
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

interface Value {
  fun typeName(): String
  override fun equals(other: Any?): Boolean
  override fun hashCode(): Int
}

interface ArrayReference : Value {
  fun getValue(i: Int): Value?
  fun length(): Int
}

interface PrimitiveValue : Value

interface DoubleValue : PrimitiveValue {
  fun value() : Double
}

interface LongValue : PrimitiveValue {
  fun value() : Long
}

interface ByteValue : PrimitiveValue {
  fun value() : Byte
}

interface CharValue : PrimitiveValue {
  fun value() : Char
}

interface FloatValue : PrimitiveValue {
  fun value() : Float
}

interface BooleanValue : PrimitiveValue {
  fun value(): Boolean
}

interface IntegerValue : PrimitiveValue {
  fun value(): Int
}

interface ShortValue : PrimitiveValue {
  fun value(): Short
}

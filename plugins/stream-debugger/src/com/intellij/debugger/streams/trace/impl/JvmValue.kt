// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.streams.core.trace.*

open class JvmValue(open val value: com.sun.jdi.Value) : Value {
  override fun typeName(): String {
    return value.type().name()
  }

  override fun toString(): String {
    return value.toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is JvmValue) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    return value.hashCode()
  }
}

class JvmArrayReference(val reference: com.sun.jdi.ArrayReference) : JvmValue(reference), ArrayReference {
  override fun getValue(i: Int): Value? = convertJvmValueToStreamValue(reference.getValue(i))
  override fun length(): Int = reference.length()
}

open class JvmPrimitiveValue(override val value: com.sun.jdi.PrimitiveValue) : JvmValue(value)

class JvmDoubleValue(override val value: com.sun.jdi.DoubleValue) : JvmPrimitiveValue(value), DoubleValue {
  override fun value() : Double = value.value()
}

class JvmLongValue(override val value: com.sun.jdi.LongValue) : JvmPrimitiveValue(value), LongValue {
  override fun value() : Long = value.value()
}

class JvmByteValue(override val value: com.sun.jdi.ByteValue) : JvmPrimitiveValue(value), ByteValue {
  override fun value() : Byte = value.value()
}

class JvmCharValue(override val value: com.sun.jdi.CharValue) : JvmPrimitiveValue(value), CharValue {
  override fun value() : Char = value.value()
}

class JvmFloatValue(override val value: com.sun.jdi.FloatValue) : JvmPrimitiveValue(value), FloatValue {
  override fun value() : Float = value.value()
}

class JvmBooleanValue(override val value: com.sun.jdi.BooleanValue) : JvmPrimitiveValue(value), BooleanValue {
  override fun value() : Boolean = value.value()
}

class JvmIntegerValue(override val value: com.sun.jdi.IntegerValue) : JvmPrimitiveValue(value), IntegerValue {
  override fun value() : Int = value.value()
}

class JvmShortValue(override val value: com.sun.jdi.ShortValue) : JvmPrimitiveValue(value), ShortValue {
  override fun value() : Short = value.value()
}

private fun convertJvmValueToStreamValue(jvmValue: com.sun.jdi.Value?) : Value? {
  when (jvmValue) {
    null -> return null
    is com.sun.jdi.ArrayReference -> return JvmArrayReference(jvmValue)
    !is com.sun.jdi.PrimitiveValue -> return JvmValue(jvmValue)
    else -> when (jvmValue) {
      is com.sun.jdi.DoubleValue -> return JvmDoubleValue(jvmValue)
      is com.sun.jdi.LongValue -> return JvmLongValue(jvmValue)
      is com.sun.jdi.ByteValue -> return JvmByteValue(jvmValue)
      is com.sun.jdi.CharValue -> return JvmCharValue(jvmValue)
      is com.sun.jdi.FloatValue -> return JvmFloatValue(jvmValue)
      is com.sun.jdi.BooleanValue -> return JvmBooleanValue(jvmValue)
      is com.sun.jdi.IntegerValue -> return JvmIntegerValue(jvmValue)
      is com.sun.jdi.ShortValue -> return JvmShortValue(jvmValue)
      else -> return JvmPrimitiveValue(jvmValue)
    }
  }
}
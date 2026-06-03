// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.sun.jdi.ArrayReference
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.DoubleValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Value

const val EMPTY_CTOR_SIGNATURE: String = "()V"

/**
 * Context for creating JDI values in the target VM during stream tracing.
 * All created objects are automatically registered in ObjectStorage to prevent GC.
 */
interface ValueContext {
  /**
   * Invokes an instance method on an object reference in the target VM.
   */
  fun Method.invoke(obj: ObjectReference, args: List<Value?> = emptyList()): Value?

  /**
   * Invokes a static method on a class type in the target VM.
   */
  fun Method.invoke(classType: ClassType, args: List<Value?> = emptyList()): Value?

  /**
   * Finds a class by name in the target VM using the context class loader.
   */
  fun clazz(className: String): ClassType

  fun clazz(cls: Class<*>): ClassType

  /**
   * Finds a method by name and JNI signature on a reference type. Throws if not found.
   */
  fun ReferenceType.method(name: String, signature: String): Method

  /**
   * Creates an instance in the target VM and registers it in ObjectStorage.
   */
  fun instance(
    cls: Class<*>,
    constructorSignature: String = EMPTY_CTOR_SIGNATURE,
    args: List<Value?> = emptyList(),
  ): ObjectReference

  fun instance(
    cls: String,
    constructorSignature: String = EMPTY_CTOR_SIGNATURE,
    args: List<Value?> = emptyList(),
  ): ObjectReference

  /**
   * Creates an array in the target VM.
   */
  fun array(componentType: String, size: Int): ArrayReference
  fun array(vararg values: Value?): ArrayReference

  fun Type.defaultValue(): Value?

  /**
   * Mirrors primitive values in JDI.
   */
  val Int.mirror: IntegerValue
  val Long.mirror: LongValue
  val Boolean.mirror: BooleanValue
  val Double.mirror: DoubleValue
}

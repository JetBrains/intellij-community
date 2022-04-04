// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.*

const val EMPTY_CONSTRUCTOR_SIGNATURE = "()V"

/**
 * @author Shumaf Lovpache
 */
interface ValueContext {
  val evaluationContext: EvaluationContextImpl

  /**
   * @throws InvalidTypeException
   * @throws ClassNotLoadedException
   * @throws IncompatibleThreadStateException
   * @throws InvocationException
   */
  fun instance(className: String, constructorSignature: String, args: List<Value>): ObjectReference

  fun instance(className: String): ObjectReference = instance(className, EMPTY_CONSTRUCTOR_SIGNATURE, emptyList())

  val Int.mirror: IntegerValue
  val Byte.mirror: ByteValue
  val Char.mirror: CharValue
  val Float.mirror: FloatValue
  val Long.mirror: LongValue
  val Short.mirror: ShortValue
  val Double.mirror: DoubleValue
  val Boolean.mirror: BooleanValue
  val String.mirror: StringReference
  val Unit.mirror: VoidValue

  fun Type.defaultValue(): Value?

  fun getType(className: String): ReferenceType

  fun array(componentType: String, size: Int): ArrayReference
  fun array(vararg values: Value?): ArrayReference
  fun array(values: List<Value?>): ArrayReference

  fun ObjectReference.method(name: String, signature: String): Method
  fun ReferenceType.method(name: String, signature: String): Method

  fun Method.invoke(cls: ClassType, arguments: List<Value?>): Value?
  fun Method.invoke(obj: ObjectReference, arguments: List<Value?>): Value?

  fun keep(value: Value?)
}
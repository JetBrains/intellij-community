// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.psi.CommonClassNames
import com.sun.jdi.*

/**
 * @author Shumaf Lovpache
 */
class ValueContextImpl(private val bytecodeFactories: Map<String, BytecodeFactory>,
                       private val objectStorage: ObjectStorage,
                       override val evaluationContext: EvaluationContextImpl) : ValueContext {
  override fun instance(className: String, constructorSignature: String, args: List<Value>): ObjectReference {
    val instance = when (val type = getType(className)) {
      // Array type don't have constructor method
      is ArrayType -> {
        val arrayLength = args.first() as IntegerValue
        type.newInstance(arrayLength.value())
      }
      is ClassType -> {
        val constructorMethod = type.methodsByName(JVMNameUtil.CONSTRUCTOR_NAME, constructorSignature).first()
                                ?: throw ValueInstantiationException(className)
        constructorMethod.prepareArguments(evaluationContext)
        val threadRef = evaluationContext.suspendContext.thread?.threadReference ?: throw ValueInstantiationException(className)
        type.newInstance(threadRef, constructorMethod, args, ClassType.INVOKE_SINGLE_THREADED)
      }
      else -> throw ValueInstantiationException(type.name())
    }

    keep(instance)
    return instance
  }

  private val virtualMachine
    get() = evaluationContext.debugProcess.virtualMachineProxy

  override val Int.mirror: IntegerValue
    get() = virtualMachine.mirrorOf(this)

  override val Byte.mirror: ByteValue
    get() = virtualMachine.mirrorOf(this)

  override val Char.mirror: CharValue
    get() = virtualMachine.mirrorOf(this)

  override val Float.mirror: FloatValue
    get() = virtualMachine.mirrorOf(this)

  override val Long.mirror: LongValue
    get() = virtualMachine.mirrorOf(this)

  override val Short.mirror: ShortValue
    get() = virtualMachine.mirrorOf(this)

  override val Double.mirror: DoubleValue
    get() = virtualMachine.mirrorOf(this)

  override val Boolean.mirror: BooleanValue
    get() = virtualMachine.mirrorOf(this)

  override val String.mirror: StringReference
    get() = virtualMachine.mirrorOf(this).also { keep(it) }

  override val Unit.mirror: VoidValue
    get() = virtualMachine.mirrorOfVoid()

  // Hack for getting proper type
  override fun Type.defaultValue(): Value? = array(this.name(), 1).getValue(0)

  override fun getType(className: String): ReferenceType = evaluationContext.loadClassIfAbsent(className) {
    tryLoadClassBytecode(className)
  } ?: throw ValueInstantiationException(className)

  override fun array(componentType: String, size: Int): ArrayReference {
    val arrayClassName = "$componentType[]"
    val vm = virtualMachine.virtualMachine
    val arraySize = vm.mirrorOf(size)
    return instance(arrayClassName, "(I)V", listOf(arraySize)) as ArrayReference
  }

  override fun array(vararg values: Value?): ArrayReference = array(values.toList())

  override fun array(values: List<Value?>): ArrayReference {
    val valueTypes = values.map { it?.type() }.distinct()
    val componentType = when {
      values.isEmpty() -> throw BreakpointTracingException("Could not infer type for empty array.")
      valueTypes.all { it is ReferenceType || it == null } -> CommonClassNames.JAVA_LANG_OBJECT
      valueTypes.size > 1 -> throw BreakpointTracingException(
        "Could not create array of non-reference types from a list of values with different types")
      valueTypes.first() is PrimitiveType -> valueTypes.first()!!.name()
      // All values of the same (but not primitive or reference) type. For ex. void value
      else -> throw BreakpointTracingException("All values in an array must be of a reference type or of the same primitive type.")
    }

    val array = array(componentType, values.size)
    array.values = values
    return array
  }

  override fun ObjectReference.method(name: String, signature: String): Method = referenceType().method(name, signature)

  override fun ReferenceType.method(name: String, signature: String): Method = methodsByName(name, signature)
                                                                                 .firstOrNull().also {
      it?.prepareArguments(evaluationContext)
    } ?: throw MethodNotFoundException(name, signature, this.name())

  override fun Method.invoke(cls: ClassType, arguments: List<Value?>): Value? {
    val returnValue = evaluationContext.debugProcess
      .invokeMethod(evaluationContext, cls, this, arguments, 0, true)

    keep(returnValue)
    return returnValue
  }

  override fun Method.invoke(obj: ObjectReference, arguments: List<Value?>): Value? {
    val returnValue = evaluationContext.debugProcess
      .invokeInstanceMethod(evaluationContext, obj, this, arguments, 0, true)

    keep(returnValue)
    return returnValue
  }

  override fun keep(value: Value?) {
    if (value != null && value is ObjectReference) {
      objectStorage.keep(evaluationContext, value)
    }
  }

  private fun tryLoadClassBytecode(className: String): ByteArray {
    val factory = bytecodeFactories[className] ?: throw ValueInstantiationException(className)
    return factory() ?: throw ValueInstantiationException(className)
  }
}
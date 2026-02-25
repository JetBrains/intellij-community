// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.ValueContext
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.DoubleValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Interface for preventing garbage collection of objects in the target VM.
 * Objects stored via this interface are protected from GC until explicitly released.
 */
interface ObjectStorage {
  fun store(obj: ObjectReference)

  fun storeAll(objects: Collection<ObjectReference>): Unit = objects.forEach { store(it) }

  fun release(obj: ObjectReference)

  fun releaseAll()

  /**
   * Scoped access to ValueContext. The only way to obtain a ValueContext.
   */
  fun <T> watch(evaluationContext: EvaluationContextImpl, block: ValueContext.() -> T): T {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return ValueContextImpl(this, evaluationContext).block()
  }
}

/**
 * ObjectStorage implementation that prevents garbage collection by disabling collection on objects.
 * Uses JDI's disableCollection() API to protect objects from GC during stream tracing.
 *
 * All methods must be called from the debugger manager thread.
 */
internal class DisableCollectionObjectStorage : ObjectStorage {
  private val registeredObjects = mutableSetOf<ObjectReference>()

  override fun store(obj: ObjectReference) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (registeredObjects.add(obj)) {
      DebuggerUtilsImpl.disableCollection(obj)
    }
  }

  override fun release(obj: ObjectReference) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (registeredObjects.remove(obj)) {
      DebuggerUtilsImpl.enableCollection(obj)
    }
  }

  override fun releaseAll() {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    registeredObjects.forEach { DebuggerUtilsImpl.enableCollection(it) }
    registeredObjects.clear()
  }
}

private class ValueContextImpl(
  private val objectStorage: ObjectStorage,
  private val evaluationContext: EvaluationContextImpl
) : ValueContext {

  private val vm = evaluationContext.virtualMachineProxy.virtualMachine

  override fun Method.invoke(obj: ObjectReference, args: List<Value?>): Value? =
    evaluationContext.debugProcess.invokeInstanceMethod(evaluationContext, obj, this, args, 0, true)

  override fun Method.invoke(classType: ClassType, args: List<Value?>): Value? =
    evaluationContext.debugProcess.invokeMethod(evaluationContext, classType, this, args, 0, true)

  override fun clazz(className: String): ClassType = loadType(className) as ClassType

  override fun clazz(cls: Class<*>): ClassType = ClassLoadingUtils.getHelperClass(cls, evaluationContext)!!

  override fun ReferenceType.method(name: String, signature: String): Method =
    DebuggerUtilsImpl.findMethod(this, name, signature)
    ?: error("Cannot find method $name$signature on ${name()}")

  override fun instance(cls: Class<*>, constructorSignature: String, args: List<Value?>): ObjectReference {
    val classType = clazz(cls)
    val constructor = classType.concreteMethodByName("<init>", constructorSignature)
    val instance = evaluationContext.debugProcess.newInstance(
      evaluationContext,
      classType,
      constructor,
      args,
      0,
      true
    )

    // Protect from GC
    objectStorage.store(instance)

    return instance
  }

  override fun array(componentType: String, size: Int): ArrayReference {
    val arrayClassName = "$componentType[]"
    val arrayClassType = loadType(arrayClassName) as ArrayType
    val arrayInstance = DebuggerUtilsEx.mirrorOfArray(arrayClassType, size, evaluationContext)
    objectStorage.store(arrayInstance)
    return arrayInstance
  }

  override fun array(vararg values: Value?): ArrayReference {
    val valueTypes = values.map { it?.type() }.distinct()
    val componentType = when {
      values.isEmpty() -> error("Could not infer type for empty array.")
      valueTypes.all { it is ReferenceType || it == null } -> CommonClassNames.JAVA_LANG_OBJECT
      valueTypes.size > 1 -> error("Could not create array of non-reference types from a list of values with different types")
      valueTypes.first() is PrimitiveType -> valueTypes.first()!!.name()
      // All values of the same (but not primitive or reference) type. For ex. void value
      else -> error("All values in an array must be of a reference type or of the same primitive type.")
    }

    val arr = array(componentType, values.size)
    arr.values = values.toList()
    return arr
  }

  override val Int.mirror: IntegerValue get() = vm.mirrorOf(this)
  override val Long.mirror: LongValue get() = vm.mirrorOf(this)
  override val Boolean.mirror: BooleanValue get() = vm.mirrorOf(this)
  override val Double.mirror: DoubleValue get() = vm.mirrorOf(this)

  private fun loadType(typeName: String): ReferenceType =
    evaluationContext.debugProcess.findClass(evaluationContext, typeName, evaluationContext.classLoader)
}
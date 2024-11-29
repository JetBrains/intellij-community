// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.base.util.evaluate

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.project.Project
import com.sun.jdi.*
import com.sun.jdi.request.EventRequest
import org.jetbrains.kotlin.idea.debugger.base.util.findMethod
import org.jetbrains.kotlin.idea.debugger.base.util.hopelessAware
import org.jetbrains.org.objectweb.asm.Type

class ExecutionContext(evaluationContext: EvaluationContextImpl, val frameProxy: StackFrameProxyImpl) :
    BaseExecutionContext(evaluationContext)

class DefaultExecutionContext(evaluationContext: EvaluationContextImpl) : BaseExecutionContext(evaluationContext) {

    constructor(suspendContext: SuspendContextImpl) : this(
        EvaluationContextImpl(
            suspendContext,
            null
        )
    )

    constructor(suspendContext: SuspendContextImpl, frameProxy: StackFrameProxyImpl?) : this(
        EvaluationContextImpl(
            suspendContext,
            frameProxy
        )
    )

    val frameProxy: StackFrameProxyImpl?
        get() =
            evaluationContext.frameProxy

    fun keepReference(ref: ObjectReference?): ObjectReference? {
        ref?.let {
            super.keepReference(ref)
        }
        return ref
    }
}

sealed class BaseExecutionContext(val evaluationContext: EvaluationContextImpl) {
    val vm: VirtualMachineProxyImpl
        get() = evaluationContext.virtualMachineProxy

    val classLoader: ClassLoaderReference?
        get() = evaluationContext.classLoader

    val suspendContext: SuspendContextImpl
        get() = evaluationContext.suspendContext

    val debugProcess: DebugProcessImpl
        get() = evaluationContext.debugProcess

    val project: Project
        get() = evaluationContext.project

    val invokePolicy = run {
        val suspendContext = evaluationContext.suspendContext
        if (suspendContext.suspendPolicy == EventRequest.SUSPEND_EVENT_THREAD) ObjectReference.INVOKE_SINGLE_THREADED else 0
    }

    @Throws(EvaluateException::class)
    fun invokeMethod(
        obj: ObjectReference,
        method: Method,
        args: List<Value?>,
        invocationOptions: Int = 0,
        internalEvaluate: Boolean = false
    ): Value? {
        return debugProcess.invokeInstanceMethod(evaluationContext, obj, method, args, invocationOptions, internalEvaluate)
    }

    fun invokeMethod(type: ClassType, method: Method, args: List<Value?>, internalEvaluate: Boolean = false): Value? {
        return debugProcess.invokeMethod(evaluationContext, type, method, args, internalEvaluate)
    }

    fun invokeMethod(type: InterfaceType, method: Method, args: List<Value?>): Value? {
        return debugProcess.invokeMethod(evaluationContext, type, method, args)
    }

    @Throws(EvaluateException::class)
    fun newInstance(type: ClassType, constructor: Method, args: List<Value?>): ObjectReference {
        return debugProcess.newInstance(evaluationContext, type, constructor, args)
    }

    @Throws(EvaluateException::class)
    fun findClass(name: String, classLoader: ClassLoaderReference? = null): ReferenceType? {
        debugProcess.findClass(evaluationContext, name, classLoader)?.let { return it }

        // If 'isAutoLoadClasses' is true, `findClass()` already did this
        if (!evaluationContext.isAutoLoadClasses) {
            try {
                debugProcess.loadClass(evaluationContext, name, classLoader)
            } catch (e: InvocationException) {
                throw EvaluateExceptionUtil.createEvaluateException(e)
            } catch (e: ClassNotLoadedException) {
                throw EvaluateExceptionUtil.createEvaluateException(e)
            } catch (e: IncompatibleThreadStateException) {
                throw EvaluateExceptionUtil.createEvaluateException(e)
            } catch (e: InvalidTypeException) {
                throw EvaluateExceptionUtil.createEvaluateException(e)
            }
        }

        return null
    }

    @Throws(EvaluateException::class)
    fun findClass(asmType: Type, classLoader: ClassLoaderReference? = null): ReferenceType? {
        if (asmType.sort != Type.OBJECT && asmType.sort != Type.ARRAY) {
            return null
        }

        return findClass(asmType.className, classLoader)
    }

    fun keepReference(reference: ObjectReference) {
        evaluationContext.keep(reference)
    }

    fun findClassSafe(className: String): ClassType? =
        hopelessAware { findClass(className, classLoader) as? ClassType }

    fun findReferenceTypeSafe(name: String): ReferenceType? =
        hopelessAware { findClass(name, classLoader) }

    fun invokeMethodSafe(type: ClassType, method: Method, args: List<Value?>): Value? {
        return hopelessAware { debugProcess.invokeMethod(evaluationContext, type, method, args) }
    }

    fun invokeMethodAsInt(instance: ObjectReference, methodName: String): Int? =
        (findAndInvoke(instance, instance.referenceType(), methodName, "()I") as? IntegerValue)?.value()

    fun invokeMethodAsObject(type: ClassType, methodName: String, vararg params: Value): ObjectReference? =
        invokeMethodAsObject(type, methodName, null, *params)

    private fun invokeMethodAsObject(type: ClassType, methodName: String, methodSignature: String?, vararg params: Value): ObjectReference? =
        findAndInvoke(type, methodName, methodSignature, *params) as? ObjectReference

    fun invokeMethodAsObject(instance: ObjectReference, methodName: String, vararg params: Value): ObjectReference? =
        invokeMethodAsObject(instance, methodName, null, *params)

    private fun invokeMethodAsObject(
        instance: ObjectReference,
        methodName: String,
        methodSignature: String?,
        vararg params: Value
    ): ObjectReference? =
        findAndInvoke(instance, methodName, methodSignature, *params) as? ObjectReference

    fun invokeMethodAsObject(instance: ObjectReference, method: Method, vararg params: Value): ObjectReference? =
        invokeMethod(instance, method, params.asList()) as? ObjectReference

    fun invokeMethodAsVoid(type: ClassType, methodName: String, methodSignature: String? = null, vararg params: Value = emptyArray()) =
        findAndInvoke(type, methodName, methodSignature, *params)

    fun invokeMethodAsVoid(
        instance: ObjectReference,
        methodName: String,
        methodSignature: String? = null,
        vararg params: Value = emptyArray()
    ) =
        findAndInvoke(instance, methodName, methodSignature, *params)

    fun invokeMethodAsArray(instance: ClassType, methodName: String, methodSignature: String, vararg params: Value): ArrayReference? =
        findAndInvoke(instance, methodName, methodSignature, *params) as? ArrayReference

    private fun findAndInvoke(
        ref: ObjectReference,
        type: ReferenceType,
        name: String,
        methodSignature: String,
        vararg params: Value
    ): Value? {
        return invokeMethod(ref, type.findMethod(name, methodSignature), params.asList())
    }

    /**
     * static method invocation
     */
    private fun findAndInvoke(type: ClassType, name: String, methodSignature: String? = null, vararg params: Value): Value? {
        return invokeMethod(type, type.findMethod(name, methodSignature), params.asList())
    }

    private fun findAndInvoke(instance: ObjectReference, name: String, methodSignature: String? = null, vararg params: Value): Value? {
        return invokeMethod(instance, instance.referenceType().findMethod(name, methodSignature), params.asList())
    }
}

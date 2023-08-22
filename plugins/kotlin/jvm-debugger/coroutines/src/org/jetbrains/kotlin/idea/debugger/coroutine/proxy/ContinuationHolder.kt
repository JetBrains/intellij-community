// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.JavaValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.VMDisconnectedException
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isAbstractCoroutine
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class ContinuationHolder private constructor(val context: DefaultExecutionContext) {
    private val debugMetadata: DebugMetadata? = DebugMetadata.instance(context)
    private val locationCache = LocationCache(context)
    private val debugProbesImpl = DebugProbesImpl.instance(context)
    private val javaLangObjectToString = JavaLangObjectToString(context)

    fun extractCoroutineInfoData(continuation: ObjectReference): CompleteCoroutineInfoData? {
        try {
            val consumer = mutableListOf<CoroutineStackFrameItem>()
            val continuationStack = debugMetadata?.fetchContinuationStack(continuation, context) ?: return null
            for (frame in continuationStack.coroutineStack) {
                val coroutineStackFrame = createStackFrameItem(frame)
                if (coroutineStackFrame != null)
                    consumer.add(coroutineStackFrame)
            }
            val lastRestoredFrame = continuationStack.coroutineStack.lastOrNull()
            return findCoroutineInformation(lastRestoredFrame?.baseContinuationImpl?.coroutineOwner, consumer)
        } catch (e: VMDisconnectedException) {
        } catch (e: Exception) {
            log.warn("Error while looking for stack frame", e)
        }
        return null
    }

    private fun findCoroutineInformation(
            coroutineOwner: ObjectReference?,
            stackFrameItems: List<CoroutineStackFrameItem>
    ): CompleteCoroutineInfoData? {
        val creationStackTrace = mutableListOf<CreationCoroutineStackFrameItem>()
        val realState = if (coroutineOwner?.type()?.isAbstractCoroutine() == true) {
            state(coroutineOwner) ?: return null
        } else {
            val ci = debugProbesImpl?.getCoroutineInfo(coroutineOwner, context)
            if (ci != null) {
                val providedCreationStackTrace = ci.creationStackTraceProvider.getStackTrace()
                if (providedCreationStackTrace != null)
                    for (index in providedCreationStackTrace.indices) {
                        val frame = providedCreationStackTrace[index]
                        val ste = frame.stackTraceElement()
                        val location = locationCache.createLocation(ste)
                        creationStackTrace.add(CreationCoroutineStackFrameItem(ste, location, index == 0))
                    }
                CoroutineDescriptor.instance(ci)
            } else {
                CoroutineDescriptor(CoroutineInfoData.DEFAULT_COROUTINE_NAME, "-1", State.UNKNOWN, null)
            }
        }
        return CompleteCoroutineInfoData(realState, stackFrameItems, creationStackTrace)
    }

    fun state(value: ObjectReference?): CoroutineDescriptor? {
        value ?: return null
        val standaloneCoroutine = StandaloneCoroutine.instance(context) ?: return null
        val standAloneCoroutineMirror = standaloneCoroutine.mirror(value, context)
        if (standAloneCoroutineMirror?.context is MirrorOfCoroutineContext) {
            val id = standAloneCoroutineMirror.context.id
            val name = standAloneCoroutineMirror.context.name ?: CoroutineInfoData.DEFAULT_COROUTINE_NAME
            val toString = javaLangObjectToString.mirror(value, context) ?: return null
            // trying to get coroutine information by calling JobSupport.toString(), ${nameString()}{${stateString(state)}}@$hexAddress
            val r = """\w+\{(\w+)}@([\w\d]+)""".toRegex()
            val matcher = r.toPattern().matcher(toString)
            if (matcher.matches()) {
                val state = stateOf(matcher.group(1))
                val hexAddress = matcher.group(2)
                return CoroutineDescriptor(name, id?.toString() ?: hexAddress, state, standAloneCoroutineMirror.context.dispatcher)
            }
        }
        return null
    }

    private fun createStackFrameItem(
        frame: MirrorOfStackFrame
    ): DefaultCoroutineStackFrameItem? {
        val stackTraceElement = frame.baseContinuationImpl.stackTraceElement?.stackTraceElement() ?: return null
        val locationClass = context.findClassSafe(stackTraceElement.className) ?: return null
        val generatedLocation = locationCache.createLocation(locationClass, stackTraceElement.methodName, stackTraceElement.lineNumber)
        val spilledVariables = frame.baseContinuationImpl.spilledValues(context)
        return DefaultCoroutineStackFrameItem(generatedLocation, spilledVariables)
    }

    companion object {
        val log by logger

        fun instance(context: DefaultExecutionContext) =
            ContinuationHolder(context)

        private fun stateOf(state: String?): State =
            when (state) {
                "Active" -> State.RUNNING
                "Cancelling" -> State.SUSPENDED_CANCELLING
                "Completing" -> State.SUSPENDED_COMPLETING
                "Cancelled" -> State.CANCELLED
                "Completed" -> State.COMPLETED
                "New" -> State.NEW
                else -> State.UNKNOWN
            }
    }
}

fun MirrorOfBaseContinuationImpl.spilledValues(context: DefaultExecutionContext): List<JavaValue> {
    return fieldVariables.map {
        it.toJavaValue(that, context)
    }
}

fun FieldVariable.toJavaValue(continuation: ObjectReference, context: DefaultExecutionContext): JavaValue {
    val valueDescriptor = ContinuationVariableValueDescriptorImpl(
        context,
        continuation,
        fieldName,
        variableName
    )
    return JavaValue.create(
        null,
        valueDescriptor,
        context.evaluationContext,
        context.debugProcess.xdebugProcess!!.nodeManager,
        false
    )
}

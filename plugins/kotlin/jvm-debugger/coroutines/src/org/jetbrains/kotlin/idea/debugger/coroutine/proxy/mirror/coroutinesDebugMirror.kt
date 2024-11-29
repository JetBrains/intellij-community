// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isSubTypeOrSame
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

class DebugProbesImpl private constructor(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfDebugProbesImpl>("kotlinx.coroutines.debug.internal.DebugProbesImpl", context) {
    private val instanceField by FieldDelegate<ObjectReference>("INSTANCE")
    private val instance = instanceField.staticValue()

    private val javaLangListMirror = JavaUtilAbstractCollection(context)
    private val coroutineInfo =
            CoroutineInfo.instance(context) ?: throw IllegalStateException("CoroutineInfo implementation not found.")
    private val debugProbesCoroutineOwner = DebugProbesImplCoroutineOwner(coroutineInfo, context)

    private val isInstalledInCoreMethod by MethodDelegate<BooleanValue>("isInstalled\$kotlinx_coroutines_debug", "()Z")
    private val isInstalledInDebugMethod by MethodDelegate<BooleanValue>("isInstalled\$kotlinx_coroutines_core", "()Z")

    private val dumpMethod by MethodMirrorDelegate("dumpCoroutinesInfo", javaLangListMirror, "()Ljava/util/List;")

    private val dumpCoroutinesInfoAsJsonAndReferences by MethodDelegate<ArrayReference>("dumpCoroutinesInfoAsJsonAndReferences", "()[Ljava/lang/Object;")

    val isInstalled: Boolean by lazy { isInstalled(context) }

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext) =
        MirrorOfDebugProbesImpl(value, instance, isInstalled)

    private fun isInstalled(context: DefaultExecutionContext): Boolean =
            isInstalledInDebugMethod.value(instance, context)?.booleanValue() ?:
            isInstalledInCoreMethod.value(instance, context)?.booleanValue()  ?:
            false

    fun dumpCoroutinesInfo(context: DefaultExecutionContext): List<MirrorOfCoroutineInfo> {
        instance ?: return emptyList()
        val referenceList = dumpMethod.mirror(instance, context) ?: return emptyList()
        return referenceList.values.mapNotNull { coroutineInfo.mirror(it, context) }
    }
    
    fun getCoroutinesRunningOnCurrentThread(context: DefaultExecutionContext): Set<Long> {
        val coroutines = dumpCoroutinesInfo(context)
        return coroutines.mapNotNull { 
            if (it.lastObservedThread == context.suspendContext.thread?.threadReference) it.sequenceNumber else null
        }.toSet()
    }
    
    fun canDumpCoroutinesInfoAsJsonAndReferences() =
        dumpCoroutinesInfoAsJsonAndReferences.method != null

    fun dumpCoroutinesInfoAsJsonAndReferences(executionContext: DefaultExecutionContext) =
        dumpCoroutinesInfoAsJsonAndReferences.value(instance, executionContext)

    fun getCoroutineInfo(
        value: ObjectReference,
        context: DefaultExecutionContext,
        coroutineContext: MirrorOfCoroutineContext,
        sequenceNumber: Long?,
        state: String?,
        lastObservedThread: ThreadReference?,
        lastObservedFrame: ObjectReference?
    ): MirrorOfCoroutineInfo {
        return coroutineInfo.fetchMirror(
            value,
            context,
            coroutineContext,
            sequenceNumber,
            state,
            lastObservedThread,
            lastObservedFrame
        )
    }

    internal fun getCoroutineInfo(value: ObjectReference?, context: DefaultExecutionContext): MirrorOfCoroutineInfo? {
        val coroutineOwner = debugProbesCoroutineOwner.mirror(value, context)
        return coroutineOwner?.coroutineInfo
    }

    fun getObject() = instance

    companion object {
        val log by logger

        fun instance(context: DefaultExecutionContext) =
                try {
                    DebugProbesImpl(context)
                }
                catch (e: IllegalStateException) {
                    log.debug("Attempt to access DebugProbesImpl but none found.", e)
                    null
                }
    }
}

class DebugProbesImplCoroutineOwner(private val coroutineInfo: CoroutineInfo?, context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfCoroutineOwner>(COROUTINE_OWNER_CLASS_NAME, context) {
    private val infoField by FieldMirrorDelegate("info", DebugCoroutineInfoImpl(context))

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineOwner? {
        val info = infoField.value(value) ?: return null
        if (infoField.isCompatible(info))
            return MirrorOfCoroutineOwner(value, infoField.mirrorOnly(info, context))
        else
            return coroutineInfo?.let { MirrorOfCoroutineOwner(value, it.mirror(info, context)) }
    }

    companion object {
        const val COROUTINE_OWNER_CLASS_NAME = "kotlinx.coroutines.debug.internal.DebugProbesImpl\$CoroutineOwner"

        fun instanceOf(value: ObjectReference?) =
                value?.referenceType()?.isSubTypeOrSame(COROUTINE_OWNER_CLASS_NAME) ?: false
    }
}

fun interface StackTraceMirrorProvider {
    fun getStackTrace(): List<MirrorOfStackTraceElement>
}

fun interface JobMirrorProvider {
    fun getJob(): MirrorOfJob?
}

class DebugCoroutineInfoImpl(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfCoroutineInfo>("kotlinx.coroutines.debug.internal.DebugCoroutineInfoImpl", context) {
    private val stackTraceElement = StackTraceElement(context)

    private val lastObservedThread by FieldDelegate<ThreadReference>("lastObservedThread")
    private val state by FieldMirrorDelegate<ObjectReference, String>("_state", JavaLangObjectToString(context))
    private val lastObservedFrame by FieldMirrorDelegate("_lastObservedFrame", WeakReference(context))
    private val sequenceNumber by FieldDelegate<LongValue>("sequenceNumber")

    private val _context by MethodMirrorDelegate("getContext", CoroutineContext(context))
    private val getCreationStackTrace by MethodMirrorDelegate("getCreationStackTrace", JavaUtilAbstractCollection(context))

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineInfo {
        val state = state.mirror(value, context)
        val coroutineContext = _context.mirror(value, context)
        return MirrorOfCoroutineInfo(
            coroutineContext,
            sequenceNumber.value(value)?.longValue(),
            state,
            lastObservedThread.value(value),
            lastObservedFrame.mirror(value, context)?.reference,
            creationStackTraceProvider = StackTraceMirrorProvider {
                val creationStackTraceMirror = getCreationStackTrace.mirror(value, context)
                creationStackTraceMirror?.values?.mapNotNull { stackTraceElement.mirror(it, context) } ?: emptyList()
            }
        )
    }
}

class CoroutineInfo private constructor(
    context: DefaultExecutionContext,
    val className: String = AGENT_134_CLASS_NAME
) : BaseMirror<ObjectReference, MirrorOfCoroutineInfo>(className, context) {
    private val stackTraceElement = StackTraceElement(context)
    private val contextFieldRef by FieldMirrorDelegate("context", CoroutineContext(context))
    private val sequenceNumberField by FieldDelegate<LongValue>("sequenceNumber")
    private val creationStackTraceMethod by MethodMirrorDelegate("getCreationStackTrace", JavaUtilAbstractCollection(context))
    private val stateMethod by MethodDelegate<StringReference>("getState", "()Ljava/lang/String;")

    private val lastObservedFrameField by FieldDelegate<ObjectReference>("lastObservedFrame")
    private val lastObservedThreadField by FieldDelegate<ThreadReference>("lastObservedThread")

    companion object {
        val log by logger
        private const val AGENT_134_CLASS_NAME = "kotlinx.coroutines.debug.CoroutineInfo"
        private const val AGENT_135_AND_UP_CLASS_NAME = "kotlinx.coroutines.debug.internal.DebugCoroutineInfo"

        fun instance(context: DefaultExecutionContext): CoroutineInfo? {
            val classType = context.findClassSafe(AGENT_135_AND_UP_CLASS_NAME) ?: context.findClassSafe(AGENT_134_CLASS_NAME) ?: return null
            return try {
                CoroutineInfo(context, classType.name())
            } catch (e: IllegalStateException) {
                log.warn("coroutine-debugger: $classType not found", e)
                null
            }
        }
    }

    fun fetchMirror(
        value: ObjectReference,
        context: DefaultExecutionContext,
        coroutineContext: MirrorOfCoroutineContext,
        sequenceNumber: Long?,
        state: String?,
        lastObservedThread: ThreadReference?,
        lastObservedFrame: ObjectReference?
    ): MirrorOfCoroutineInfo {
        return MirrorOfCoroutineInfo(
            coroutineContext,
            sequenceNumber,
            state,
            lastObservedThread,
            lastObservedFrame,
            getCreationStackTraceProvider(value, context)
        )
    }

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineInfo {
        val state = stateMethod.value(value, context)
        val coroutineContext = contextFieldRef.mirror(value, context)
        val sequenceNumber = sequenceNumberField.value(value)?.longValue()
        return MirrorOfCoroutineInfo(
            coroutineContext,
            sequenceNumber,
            state?.value(),
            lastObservedThreadField.value(value),
            lastObservedFrameField.value(value),
            getCreationStackTraceProvider(value, context)
        )
    }

    fun getContextRef(value: ObjectReference) = contextFieldRef.value(value)

    private fun getCreationStackTraceProvider(value: ObjectReference, context: DefaultExecutionContext) =
        StackTraceMirrorProvider {
            val creationStackTraceMirror = creationStackTraceMethod.mirror(value, context)
            creationStackTraceMirror?.values?.mapNotNull { stackTraceElement.mirror(it, context) } ?: emptyList()
        }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.BreakpointStepMethodFilter
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.StepRequest
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import java.lang.reflect.Field

internal class RequestHintWithMethodFilter(
    stepThread: ThreadReferenceProxyImpl,
    suspendContext: SuspendContextImpl,
    @MagicConstant(intValues = [StepRequest.STEP_INTO.toLong(), StepRequest.STEP_OVER.toLong(), StepRequest.STEP_OUT.toLong()]) depth: Int,
    methodFilter: MethodFilter
) : RequestHint(stepThread, suspendContext, methodFilter) {
    private var targetMethodMatched = false

    init {
        // NOTE: Debugger API. Open RequestHint constructor with depth
        if (depth != StepRequest.STEP_INTO) {
            findFieldWithValue(StepRequest.STEP_INTO, Integer.TYPE)?.setInt(this, depth)
        }
    }

    private fun findFieldWithValue(@Suppress("SameParameterValue") value: Int, @Suppress("SameParameterValue") type: Class<*>): Field? {
        return RequestHint::class.java.declaredFields.firstOrNull { field ->
            if (field.type == type) {
                field.isAccessible = true
                if (field.getInt(this) == value) {
                    return@firstOrNull true
                }
            }

            false
        }
    }

    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        try {
            val frameProxy = context.frameProxy
            val filter = methodFilter

            if (filter != null && frameProxy != null && filter !is BreakpointStepMethodFilter) {
                /*NODE: Debugger API. Base implementation works only for smart step into, and calls filter only if !isTheSameFrame(context). */
                if (filter.locationMatches(context.debugProcess, frameProxy.safeLocation())) {
                    targetMethodMatched = true
                    return filter.onReached(context, this)
                }
            }
        } catch (ignored: VMDisconnectedException) {
            return STOP
        } catch (e: EvaluateException) {
            LOG.error(e)
            return STOP
        }

        return super.getNextStepDepth(context)
    }

    override fun wasStepTargetMethodMatched(): Boolean {
        return super.wasStepTargetMethodMatched() || targetMethodMatched
    }
}

private val LOG = Logger.getInstance(RequestHintWithMethodFilter::class.java)

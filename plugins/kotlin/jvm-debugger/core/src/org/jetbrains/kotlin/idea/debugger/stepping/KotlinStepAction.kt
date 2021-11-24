// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.SuspendContextImpl
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.stepping.filter.KotlinStepOverFilter
import org.jetbrains.kotlin.idea.debugger.stepping.filter.LocationToken
import org.jetbrains.kotlin.idea.debugger.stepping.filter.StepOverCallerInfo

sealed class KotlinStepAction {
    object JvmStepOver : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return debugProcess.run {
                StepOverCommand(suspendContext, ignoreBreakpoints, null, StepRequest.STEP_LINE)
            }
        }
    }

    class StepInto(private val filter: MethodFilter?) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createStepIntoCommand(debugProcess, suspendContext, ignoreBreakpoints, filter, StepRequest.STEP_LINE)
        }
    }

    object StepOut : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createStepOutCommand(debugProcess, suspendContext)
        }
    }

    class KotlinStepOver(private val tokensToSkip: Set<LocationToken>, private val callerInfo: StepOverCallerInfo) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            val filter = KotlinStepOverFilter(debugProcess.project, tokensToSkip, callerInfo)
            return KotlinStepActionFactory.createKotlinStepOverCommand(debugProcess, suspendContext, ignoreBreakpoints, filter)
        }
    }

    class KotlinStepInto(private val filter: MethodFilter?) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createKotlinStepIntoCommand(debugProcess, suspendContext, ignoreBreakpoints, filter)
        }
    }

    abstract fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand
}

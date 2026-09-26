// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.SuspendContextImpl
import com.sun.jdi.request.StepRequest

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

    class StepOut(private val filter: MethodFilter? = null) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createStepOutCommand(debugProcess, suspendContext, filter)
        }
    }

    class KotlinStepOver(private val filter: KotlinMethodFilter, private val stepSize: Int = StepRequest.STEP_LINE) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createKotlinStepOverCommand(debugProcess, suspendContext, ignoreBreakpoints, filter, stepSize)
        }
    }

    class KotlinStepInto(private val filter: MethodFilter?) : KotlinStepAction() {
        override fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand {
            return KotlinStepActionFactory.createKotlinStepIntoCommand(debugProcess, suspendContext, ignoreBreakpoints, filter)
        }
    }

    abstract fun createCommand(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.StepCommand
}

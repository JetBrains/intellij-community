// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.*
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.statistics.Engine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Location
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.isOnSuspensionPoint
import org.jetbrains.kotlin.idea.debugger.core.stepping.CoroutineJobInfo.Companion.extractJobInfo
import org.jetbrains.kotlin.idea.debugger.core.stepping.KotlinStepAction.KotlinStepInto

object DebuggerSteppingHelper {
  private val LOG = Logger.getInstance(DebuggerSteppingHelper::class.java)

  fun createStepOverCommand(
    suspendContext: SuspendContextImpl,
    ignoreBreakpoints: Boolean,
    sourcePosition: SourcePosition?
  ): DebugProcessImpl.ResumeCommand {
    val debugProcess = suspendContext.debugProcess

    return with(debugProcess) {
      object : DebugProcessImpl.ResumeCommand(suspendContext) {
        override fun contextAction(suspendContext: SuspendContextImpl) {
          val frameProxy = suspendContext.frameProxy
          val location = frameProxy?.safeLocation()

          if (location != null) {
            try {
              getStepOverAction(location, suspendContext, frameProxy)
                .createCommand(debugProcess, suspendContext, ignoreBreakpoints)
                .contextAction(suspendContext)
              return
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }

          debugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints).contextAction(suspendContext)
        }
      }
    }
  }

  fun createStepOverCommandForSuspendSwitch(suspendContext: SuspendContextImpl, nextLocationAfterResume: Location?): DebugProcessImpl.StepOverCommand {
    return with(suspendContext.debugProcess) {
      object : DebugProcessImpl.StepOverCommand(suspendContext, false, null, StepRequest.STEP_MIN) {
        override fun getHint(suspendContext: SuspendContextImpl,
                             stepThread: ThreadReferenceProxyImpl,
                             parentHint: RequestHint?): RequestHint {
          val hint: RequestHint =
            object : RequestHint(stepThread, suspendContext, StepRequest.STEP_MIN, StepRequest.STEP_OVER, myMethodFilter, parentHint) {
              override fun getNextStepDepth(context: SuspendContextImpl): Int {
                if (context.frameProxy?.isOnSuspensionPoint() == true && nextLocationAfterResume != null) {
                    val filterThread = context.debugProcess.requestsManager.filterThread
                    // step till the next instruction after the resume location
                    thisLogger().debug("Stepping to the resumeLocation in method ${context.location}, filterThread = $filterThread, resumeLocationCodeIndex = ${nextLocationAfterResume.codeIndex()}, currentIndex = ${context.location?.codeIndex()}")
                    val currentLocation = context.location ?: return super.getNextStepDepth(context)
                    // Make sure that we are stepping to the nextLocationAfterResume in the correct method.
                    if (nextLocationAfterResume.safeMethod() != currentLocation.safeMethod()) {
                        thisLogger().debug("Expected to step in the resumed method ${nextLocationAfterResume.safeMethod()}, but currently stepping in ${currentLocation.safeMethod()}")
                        return StepRequest.STEP_OVER
                    }
                    if (currentLocation.codeIndex() < nextLocationAfterResume.codeIndex()) return StepRequest.STEP_OVER
                    if (currentLocation.codeIndex() == nextLocationAfterResume.codeIndex()) {
                        thisLogger().debug("Reached resumeLocation, currentIndex = ${currentLocation.codeIndex()}, filterThread = $filterThread -> STOP")
                        return STOP
                    }
                }
                return super.getNextStepDepth(context)
              }
            }
          hint.isIgnoreFilters = suspendContext.debugProcess.session.shouldIgnoreSteppingFilters()
          return hint
        }

        override fun getEngine() = Engine.KOTLIN
      }
    }
  }

  fun createStepOutCommand(suspendContext: SuspendContextImpl, ignoreBreakpoints: Boolean): DebugProcessImpl.ResumeCommand {
    return with(suspendContext.debugProcess) {
      object : DebugProcessImpl.ResumeCommand(suspendContext) {
        override fun contextAction(suspendContext: SuspendContextImpl) {
          val frameProxy = suspendContext.frameProxy
          val location = frameProxy?.safeLocation()

          if (location != null) {
            try {
              getStepOutAction(location, frameProxy)
                .createCommand(suspendContext.debugProcess, suspendContext, ignoreBreakpoints)
                .contextAction(suspendContext)
              return
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }

          suspendContext.debugProcess.createStepOutCommand(suspendContext).contextAction(suspendContext)
        }
      }
    }
  }

  fun createStepIntoCommand(
    suspendContext: SuspendContextImpl,
    ignoreBreakpoints: Boolean,
    methodFilter: MethodFilter?
  ): DebugProcessImpl.ResumeCommand {
    return with(suspendContext.debugProcess) {
      object : DebugProcessImpl.ResumeCommand(suspendContext) {
        override fun contextAction(suspendContext: SuspendContextImpl) {
          try {
            KotlinStepInto(methodFilter)
              .createCommand(suspendContext.debugProcess, suspendContext, ignoreBreakpoints)
              .contextAction(suspendContext)
          }
          catch (e: Exception) {
            suspendContext.debugProcess.createStepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter).contextAction(
              suspendContext)
          }
        }
      }
    }
  }

  fun createRunToCursorCommand(
    suspendContext: SuspendContextImpl,
    position: XSourcePosition,
    ignoreBreakpoints: Boolean
  ): DebugProcessImpl.ResumeCommand {
    val debugProcess = suspendContext.debugProcess
    return with(debugProcess) {
      object : DebugProcessImpl.RunToCursorCommand(suspendContext, position, ignoreBreakpoints) {
        val myThreadFilter = lazy { extractJobInfo(suspendContext) ?: super.getThreadFilterFromContext(suspendContext) }

        override fun shouldExecuteRegardlessOfRequestWarnings() = true

        override fun contextAction(context: SuspendContextImpl) {
          // clear stepping through to allow switching threads in case of suspend thread context
          if (myThreadFilter.value !is RealThreadInfo) {
            context.debugProcess.session.clearSteppingThrough()
          }
          super.contextAction(context)
        }

        override fun getThreadFilterFromContext(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? = myThreadFilter.value
      }
    }
  }
}

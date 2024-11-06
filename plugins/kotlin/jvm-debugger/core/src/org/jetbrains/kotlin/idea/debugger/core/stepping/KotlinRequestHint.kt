// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.DebugProcess.JAVA_STRATUM
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.jdi.ClassTypeImpl
import com.sun.jdi.Location
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.*
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.org.objectweb.asm.Type

open class KotlinRequestHint(
    stepThread: ThreadReferenceProxyImpl,
    suspendContext: SuspendContextImpl,
    stepSize: Int,
    depth: Int,
    filter: MethodFilter?,
    parentHint: RequestHint?
) : RequestHint(stepThread, suspendContext, stepSize, depth, filter, parentHint) {
    private val myInlineFilter = createKotlinInlineFilter(suspendContext)
    override fun isTheSameFrame(context: SuspendContextImpl) =
        super.isTheSameFrame(context) && (myInlineFilter === null || !myInlineFilter.isNestedInline(context))

    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        if (needTechnicalStepInto(context)) {
            return StepRequest.STEP_INTO
        }
        return super.getNextStepDepth(context)
    }

    override fun doStep(debugProcess: DebugProcessImpl, suspendContext: SuspendContextImpl?, stepThread: ThreadReferenceProxyImpl?, size: Int, depth: Int, commandToken: Any?) {
        if (depth == StepRequest.STEP_OUT) {
            val frameProxy = suspendContext?.frameProxy
            val location = frameProxy?.safeLocation()
            if (location !== null) {
                val action = getStepOutAction(location, frameProxy)
                if (action !is KotlinStepAction.StepOut) {
                    val command = action.createCommand(debugProcess, suspendContext, false)
                    val hint = command.getHint(suspendContext, stepThread, this)!!
                    command.step(suspendContext, stepThread, hint, commandToken)
                    return
                }
            }
        }
        super.doStep(debugProcess, suspendContext, stepThread, size, depth, commandToken)
    }
}

class KotlinStepOutRequestHint(
    private val returnAfterSuspendLocation: Location?,
    stepThread: ThreadReferenceProxyImpl,
    suspendContext: SuspendContextImpl,
    stepSize: Int,
    depth: Int,
    filter: MethodFilter?,
    parentHint: RequestHint?
): RequestHint(stepThread, suspendContext, stepSize, depth, filter, parentHint) {
    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        val currentLocation = context.location
        if (currentLocation != null && returnAfterSuspendLocation != null) {
            /*
               If the coroutine suspends, then the suspending function returns COROUTINE_SUSPENDED:
               if (foo(arg) == COROUTINE_SUSPENDED) {
                  return COROUTINE_SUSPENDED // returnAfterSuspendLocation
               }
               If the execution reaches this suspend return instruction, the coroutine was suspended ->
               resume and wait till the resume breakpoint set at the caller method is reached.
             */
            val currentMethod = currentLocation.safeMethod()
            // Make sure that we are stepping to the returnAfterSuspendLocation in the correct method;
            if (currentMethod != null && currentMethod != returnAfterSuspendLocation.safeMethod()) {
                // Before stop at the caller method frame, we can get to Foo$Continuation.invokeSuspend first (in case a suspension happened before step out e.g.)
                if (isInSuspendMethod(currentLocation) && currentLocation.safeLineNumber() < 0) return StepRequest.STEP_OVER
                // Otherwise perform regular step out.
                return super.getNextStepDepth(context)
            }
            val filterThread = context.debugProcess.requestsManager.filterThread
            thisLogger().debug("KotlinStepOutRequestHint: stepping to the suspend RETURN in method ${currentLocation.method()?.name()}, filterThread = $filterThread, resumeLocationIndex = $returnAfterSuspendLocation, currentIndex = ${currentLocation.codeIndex()}")
            if (currentLocation.codeIndex() < returnAfterSuspendLocation.codeIndex()) return StepRequest.STEP_OVER
            if (currentLocation.codeIndex() == returnAfterSuspendLocation.codeIndex()) {
                thisLogger().debug("KotlinStepOutRequestHint: reached suspend RETURN, currentIndex = ${currentLocation.codeIndex()} -> RESUME")
                return RESUME
            }
        }
        return super.getNextStepDepth(context)
    }
}

// Originally copied from RequestHint
class KotlinStepOverRequestHint(
    stepThread: ThreadReferenceProxyImpl,
    suspendContext: SuspendContextImpl,
    private val filter: KotlinMethodFilter,
    parentHint: RequestHint?,
    stepSize: Int
) : RequestHint(stepThread, suspendContext, stepSize, StepRequest.STEP_OVER, filter, parentHint) {
    private companion object {
        private val LOG = Logger.getInstance(KotlinStepOverRequestHint::class.java)
    }

    private class LocationData(val method: String, val signature: Type, val declaringType: String) {
        companion object {
            fun create(location: Location?): LocationData? {
                val method = location?.safeMethod() ?: return null
                val signature = Type.getMethodType(method.signature())
                return LocationData(method.name(), signature, location.declaringType().name())
            }
        }
    }

    private val startLocation = LocationData.create(suspendContext.getLocationCompat())

    private var hasBeenAtSuspensionSwitcher = false

    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        try {
            val frameProxy = context.frameProxy ?: return STOP
            if (isTheSameFrame(context)) {
                if (frameProxy.isOnSuspensionPoint()) {
                    isIgnoreFilters = true
                    hasBeenAtSuspensionSwitcher = true
                    return StepRequest.STEP_OVER
                }

                val location = frameProxy.safeLocation()
                val isAcceptable = location != null && filter.locationMatches(context, location)
                return if (isAcceptable) STOP else StepRequest.STEP_OVER
            } else if (isSteppedOut) {
                if (hasBeenAtSuspensionSwitcher) {
                    return RESUME
                }
                val location = frameProxy.safeLocation()

                if (needTechnicalStepInto(context)) {
                    return StepRequest.STEP_INTO
                }
                processSteppingFilters(context, location)?.let { return it }

                val method = location?.safeMethod()
                if (method != null && method.isSyntheticMethodForDefaultParameters() &&
                    isSteppedFromDefaultParamsOriginal(location)) {
                    return StepRequest.STEP_OVER
                }

                val lineNumber = location?.safeLineNumber(JAVA_STRATUM) ?: -1
                return if (lineNumber >= 0) STOP else StepRequest.STEP_OVER
            }
            return StepRequest.STEP_OUT
        } catch (ignored: VMDisconnectedException) {
        } catch (e: EvaluateException) {
            LOG.error(e)
        }

        return STOP
    }

    private fun isSteppedFromDefaultParamsOriginal(location: Location): Boolean {
        val startLocation = this.startLocation ?: return false
        val endLocation = LocationData.create(location) ?: return false

        if (startLocation.declaringType != endLocation.declaringType) {
            return false
        }

        if (startLocation.method + JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX != endLocation.method) {
            return false
        }

        val startArgs = startLocation.signature.argumentTypes
        val endArgs = endLocation.signature.argumentTypes

        if (startArgs.size >= endArgs.size) {
            // Default params function should always have at least one additional flag parameter
            return false
        }

        for ((index, type) in startArgs.withIndex()) {
            if (endArgs[index] != type) {
                return false
            }
        }

        for (index in startArgs.size until (endArgs.size - 1)) {
            if (endArgs[index].sort != Type.INT) {
                return false
            }
        }

        return endArgs[endArgs.size - 1].descriptor == "Ljava/lang/Object;"
    }
}

interface StopOnReachedMethodFilter

class KotlinStepIntoRequestHint(
    stepThread: ThreadReferenceProxyImpl,
    suspendContext: SuspendContextImpl,
    filter: MethodFilter?,
    parentHint: RequestHint?
) : KotlinRequestHint(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_INTO, filter, parentHint) {
    private var lastWasKotlinFakeLineNumber = false

    private companion object {
        private val LOG = Logger.getInstance(KotlinStepIntoRequestHint::class.java)
    }

    override fun getNextStepDepth(context: SuspendContextImpl): Int {
        try {
            val frameProxy = context.frameProxy ?: return STOP
            if (isTheSameFrame(context)) {
                if (frameProxy.isOnSuspensionPoint()) {
                    // Coroutine will sleep now so we can't continue stepping.
                    // Let's put a run-to-cursor breakpoint and resume the debugger.
                    return if (!CoroutineBreakpointFacility.installResumeBreakpointInCurrentMethod(context)) STOP else RESUME
                }
            }
            val location = frameProxy.safeLocation()
            // Continue stepping into if we are at a compiler generated fake line number.
            if (location != null && isKotlinFakeLineNumber(location)) {
                lastWasKotlinFakeLineNumber = true
                return StepRequest.STEP_INTO
            }
            // If the last line was a fake line number, and we are not smart-stepping,
            // the next non-fake line number is always of interest (otherwise, we wouldn't
            // have had to insert the fake line number in the first place).
            if (lastWasKotlinFakeLineNumber && methodFilter == null) {
                lastWasKotlinFakeLineNumber = false
                return STOP
            }

            val filter = methodFilter
            if (filter is StopOnReachedMethodFilter && filter.locationMatches(context.debugProcess, location)) {
                return STOP
            }

            return super.getNextStepDepth(context)
        } catch (ignored: VMDisconnectedException) {
        } catch (e: EvaluateException) {
            LOG.error(e)
        }
        return STOP
    }
}

private fun needTechnicalStepInto(context: SuspendContextImpl): Boolean {
    val location = context.location ?: return false

    // TODO: This is a hack for all coroutine builders declared in kotlinx.coroutines.Builders.common.kt
    if (context.location?.declaringType()?.name() == "kotlinx.coroutines.BuildersKt") {
        return true
    }

    if (!location.isInKotlinSources()) {
        return false
    }

    if (location.method()?.name() == "invoke" &&
        (location.declaringType() as? ClassTypeImpl)?.superclass()?.name() == "kotlin.coroutines.jvm.internal.SuspendLambda") {
        return true
    }

    if (isInSuspendMethod(location) && isOnSuspendReturnOrReenter(location) && !isOneLineMethod(location)) {
        return true
    }

    //stepped out from suspend function
    val method = location.safeMethod()
    if (method != null && isInvokeSuspendMethod(method) && location.safeLineNumber() < 0) {
        return true
    }
    return false
}
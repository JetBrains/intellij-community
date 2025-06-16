// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.breakpoints.SteppingBreakpoint
import com.intellij.debugger.ui.breakpoints.SyntheticLineBreakpoint
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.LocatableEvent
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.getLocationOfNextInstructionAfterResume

object CoroutineBreakpointFacility {
    fun installResumeBreakpointInCurrentMethod(suspendContext: SuspendContextImpl): Boolean {
        val currentLocation = suspendContext.location ?: return false
        val methodLineLocations = currentLocation.method().allLineLocations()
        // In case of stepping over the last closing bracket -> step out.
        // For a suspend block, the location of the closing bracket is previous to the last
        // (the last location is the resume location and corresponds to the first line of the function).
        val resumeLocation = if (methodLineLocations.size > 2 && methodLineLocations[methodLineLocations.size - 2] == currentLocation) {
            StackFrameInterceptor.instance?.callerLocation(suspendContext)
        } else {
            currentLocation
        } ?: return false
        val nextLocationAfterResume = getLocationOfNextInstructionAfterResume(resumeLocation)
        thisLogger().debug {
            "Trying to set a resume breakpoint in the current method: " +
                    "resumeMethod: ${resumeLocation.safeMethod()}, " +
                    "nextCallLocationLine = ${nextLocationAfterResume?.lineNumber()}"
        }
        return installCoroutineResumedBreakpoint(suspendContext, resumeLocation, nextLocationAfterResume)
    }

    fun installResumeBreakpointInCallerMethod(suspendContext: SuspendContextImpl): Boolean {
        val resumeLocation = StackFrameInterceptor.instance?.callerLocation(suspendContext) ?: return false
        val nextLocationAfterResume = getLocationOfNextInstructionAfterResume(resumeLocation)
        thisLogger().debug { "Trying to set a resume breakpoint in the caller method: " +
                "resumeMethod: ${resumeLocation.safeMethod()}, " +
                "nextCallLocationLine = ${nextLocationAfterResume?.lineNumber()}"
        }
        return installCoroutineResumedBreakpoint(suspendContext, resumeLocation, nextLocationAfterResume)
    }

    private fun installCoroutineResumedBreakpoint(context: SuspendContextImpl, resumedLocation: Location, nextLocationAfterResume: Location?): Boolean {
        val debugProcess = context.debugProcess
        debugProcess.cancelSteppingBreakpoints()
        val clearSteppingBreakpoint = installBreakpointToRemoveSteppingInCurrentThread(context)
        if (clearSteppingBreakpoint == null) {
            thisLogger().warn("No clear stepping breakpoint installed for context $context")
        }
        val project = debugProcess.project

        val useCoroutineIdFiltering = Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")
        val method = resumedLocation.safeMethod() ?: return false

        if (debugProcess.requestsManager.filterThread == null) {
            thisLogger().error("Coroutine filter should be calculated and set before breakpoint request created. " +
                "In other case, this breakpoint may be hit while intermediate evaluations on other threads before the filter will be set.")
        }

        val breakpoint = object : StepIntoMethodBreakpoint(method.declaringType().name(), method.name(), method.signature(), project) {
            override fun isRestoreBreakpoints(): Boolean = false
            override fun stopOnlyInBaseClass(): Boolean = true

            override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
                thisLogger().debug { "Hit the resume breakpoint at ${context.location}" }
                val result = super.processLocatableEvent(action, event)
                if (result) {
                    debugProcess.requestsManager.deleteRequest(this) // breakpoint is hit - disable the request already
                }

                // support same thread old-way stepping
                if (!result) return false

                val suspendContextImpl = action.suspendContext ?: return true
                clearSteppingBreakpoint?.let {
                    if (!it.steppingRemoved) {
                        thisLogger().debug("Clear old stepping from resume breakpoint")
                        it.removeRequestAndStepping(suspendContextImpl)
                    }
                }

                return scheduleStepOverCommandForSuspendSwitch(suspendContextImpl, nextLocationAfterResume)
            }

            private fun scheduleStepOverCommandForSuspendSwitch(it: SuspendContextImpl, nextLocationAfterResume: Location?): Boolean {
                DebuggerSteppingHelper.createStepOverCommandForSuspendSwitch(it, nextLocationAfterResume).prepareSteppingRequestsAndHints(it)
                // false return value will resume the execution in the `DebugProcessEvents` and
                // the scheduled above steps will perform stepping through the coroutine switch until line location.
                return false
            }
        }

        breakpoint.suspendPolicy = context.suspendPolicyFromRequestors
        if (!useCoroutineIdFiltering) {
            applyEmptyThreadFilter(debugProcess)
        }
        breakpoint.createRequest(debugProcess)
        debugProcess.setSteppingBreakpoint(breakpoint)

        val filterThread = debugProcess.requestsManager.filterThread
        thisLogger().debug { "Resume breakpoint for $method in thread $filterThread" }

        return true
    }

    private fun installBreakpointToRemoveSteppingInCurrentThread(context: SuspendContextImpl): ClearSteppingBreakpoint? {
        val classLoader = context.frameProxy?.classLoader ?: return null
        val originalThread = context.thread?.threadReference ?: return null

        val debugProbesImpl =
            context.debugProcess.findLoadedClass(context, "kotlinx.coroutines.debug.internal.DebugProbesImpl", classLoader) ?: return null

        val methods = debugProbesImpl.methods() ?: return null

        val probeResumeMethod = methods.singleOrNull { it.name().contains("probeCoroutineResumed") }  ?: return null
        val probeSuspendedMethod = methods.singleOrNull { it.name().contains("probeCoroutineSuspended") }  ?: return null

        val project = context.debugProcess.project
        val coroutineSuspendedBreakpoint = ClearSteppingBreakpoint(project, originalThread)

        installAdditionalBreakpoint(probeSuspendedMethod, coroutineSuspendedBreakpoint, context)

        val coroutineResumedBreakpoint = object : AdditionalProbeBreakpoint(project) {
            override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
                // Why in this test so many times resume is triggered???
                coroutineSuspendedBreakpoint.counter++
                thisLogger().debug { "It seems we enter undispatched coroutine in $originalThread" }
                return false
            }
        }
        installAdditionalBreakpoint(probeResumeMethod, coroutineResumedBreakpoint, context)
        coroutineSuspendedBreakpoint.probeResumeBreakpoint = coroutineResumedBreakpoint

        return coroutineSuspendedBreakpoint
    }

    private fun installAdditionalBreakpoint(probeMethod: Method, additionalRequestor: AdditionalProbeBreakpoint, context: SuspendContextImpl) {
        val originalThread = context.thread?.threadReference ?: return
        val locationForBP = probeMethod.locationOfCodeIndex(0)
        additionalRequestor.suspendPolicy = DebuggerSettings.SUSPEND_THREAD

        val requestsManager = context.debugProcess.requestsManager
        val request = requestsManager.createBreakpointRequest(additionalRequestor, locationForBP)
        request.addThreadFilter(originalThread)
        requestsManager.enableRequest(request)
        context.debugProcess.setSteppingBreakpoint(additionalRequestor)
    }
}

private abstract class AdditionalProbeBreakpoint(project: Project) : SyntheticLineBreakpoint(project), SteppingBreakpoint {
    override fun shouldIgnoreThreadFiltering() = true

    override fun track() = false

    override fun isRestoreBreakpoints() = false

    override fun setRequestHint(hint: RequestHint) {
        error("Should not be called")
    }
}

private class ClearSteppingBreakpoint(project: Project, private val originalThread: ThreadReference) : AdditionalProbeBreakpoint(project) {
    var counter = 1

    lateinit var probeResumeBreakpoint: AdditionalProbeBreakpoint

    var steppingRemoved = false
        private set

    override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
        counter--
        val suspendContext = action.suspendContext
        val currentThread = suspendContext?.thread?.threadReference
        if (originalThread == currentThread) {
            if (counter == 0) {
                removeRequestAndStepping(suspendContext)
            } else {
                thisLogger().debug { "Exit from undispatched coroutine in $currentThread, new counter = $counter" }
            }
        } else {
            // This should not happen because of the filter on the thread for this request
            thisLogger().error("Skip remove stepping breakpoint for thread ${originalThread.name()}")
        }
        return false
    }

    fun removeRequestAndStepping(suspendContext: SuspendContextImpl) {
        if (steppingRemoved) {
            return
        }
        steppingRemoved = true
        thisLogger().debug { "Remove stepping requests $suspendContext" }
        DebugProcessEvents.removeStepRequests(suspendContext, originalThread)
        suspendContext.debugProcess.requestsManager.deleteRequest(this)
        suspendContext.debugProcess.requestsManager.deleteRequest(probeResumeBreakpoint)
    }
}

fun SuspendContextImpl.getLocationCompat(): Location? {
    return this.location
}

private fun applyEmptyThreadFilter(debugProcess: DebugProcessImpl) {
    // TODO this is nasty. Find a way to apply an empty thread filter only to the newly created breakpoint
    // TODO consider moving this filtering to event loop?
    val breakpointManager = DebuggerManagerEx.getInstanceEx(debugProcess.project).breakpointManager
    breakpointManager.removeThreadFilter(debugProcess)
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.StepIntoMethodBreakpoint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.event.LocatableEvent

object CoroutineBreakpointFacility : AbstractCoroutineBreakpointFacility() {
    override fun installCoroutineResumedBreakpoint(context: SuspendContextImpl, location: Location, method: Method): Boolean {
        val debugProcess = context.debugProcess
        val project = debugProcess.project

        val breakpoint = object : StepIntoMethodBreakpoint(location.declaringType().name(), method.name(), method.signature(), project) {
            override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
                val result = super.processLocatableEvent(action, event)
                if (!result) return false

                stepOverSuspendSwitch(action, debugProcess)
                // false return value will resume the execution in the `DebugProcessEvents` and
                // the scheduled above steps will perform stepping through the coroutine switch until line location.
                return false
            }
        }
        breakpoint.setSuspendPolicy(context)
        applyEmptyThreadFilter(debugProcess)
        breakpoint.createRequest(debugProcess)
        debugProcess.setSteppingBreakpoint(breakpoint)

        return true
    }
}

fun SuspendContextImpl.getLocationCompat(): Location? {
    return this.location
}
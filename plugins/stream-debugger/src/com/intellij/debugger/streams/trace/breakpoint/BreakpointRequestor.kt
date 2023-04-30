// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.equalBySignature
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sun.jdi.Method
import com.sun.jdi.event.ExceptionEvent
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodEntryEvent
import com.sun.jdi.event.MethodExitEvent

private val LOG = logger<MethodBreakpointRequestor>()

typealias MethodEntryCallback = (requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: MethodEntryEvent) -> Unit
typealias MethodExitCallback = (requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: MethodExitEvent) -> Unit
typealias ExceptionCallback = (requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: ExceptionEvent) -> Unit

/**
 * @author Shumaf Lovpache
 */
abstract class MethodBreakpointRequestor(project: Project, private val method: Method) : FilteredRequestorImpl(project) {
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
    if (event == null) return false
    val context = action.suspendContext ?: return false

    val currentExecutingMethod = event.location().method()

    // isSuspended == true instead of isSuspended because isSuspended has nullable type Boolean?
    if (context.thread?.isSuspended == true && currentExecutingMethod.equalBySignature(method)) {
      try {
        invokeCallback(this, context, event)
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
    }

    return false
  }

  abstract fun invokeCallback(requestor: MethodBreakpointRequestor, context: SuspendContextImpl, event: LocatableEvent)

  override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_THREAD
}

class MethodEntryRequestor(
  project: Project,
  method: Method,
  private val callback: MethodEntryCallback
) : MethodBreakpointRequestor(project, method) {
  override fun invokeCallback(requestor: MethodBreakpointRequestor, context: SuspendContextImpl, event: LocatableEvent) {
    if (event !is MethodEntryEvent) return

    callback(requestor, context, event)
  }
}

class MethodExitRequestor(
  project: Project,
  method: Method,
  private val callback: MethodExitCallback
) : MethodBreakpointRequestor(project, method) {
  override fun invokeCallback(requestor: MethodBreakpointRequestor, context: SuspendContextImpl, event: LocatableEvent) {
    if (event !is MethodExitEvent) return

    callback(requestor, context, event)
  }
}

class ExceptionBreakpointRequestor(project: Project, private val callback: ExceptionCallback) : FilteredRequestorImpl(project) {
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
    if (event == null || event !is ExceptionEvent) return false
    val context = action.suspendContext ?: return false
    if (context.thread?.isSuspended == true) {
      try {
        callback(this, context, event)
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
    }

    return false
  }

  override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_THREAD
}
// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.exec.breakpoint

import com.intellij.debugger.streams.test.TraceExecutionTestCase
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointResolver
import com.intellij.debugger.streams.trace.breakpoint.MethodBreakpointTracer
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XDebugSession

/**
 * @author Shumaf Lovpache
 */
open class BreakpointBasedTraceExecutionTestCase : TraceExecutionTestCase() {
  override fun getStreamTracer(session: XDebugSession, resultInterpreter: TraceResultInterpreter): StreamTracer {
    val currentFile = runReadAction {
      val psiManager = PsiManager.getInstance(session.project)
      val currentPosition = session.currentPosition!!
      psiManager.findFile(currentPosition.file)!!
    }
    val breakpointResolver = JavaBreakpointResolver(currentFile)

    return MethodBreakpointTracer(session, breakpointResolver, resultInterpreter)
  }
}
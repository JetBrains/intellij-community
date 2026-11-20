// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler

/** Steps out of the code block of the paused line, like the Java action "Step Out of Code Block". */
internal class PyStepOutOfBlockAction : XDebuggerActionBase() {
  private val handler = object : XDebuggerSuspendedActionHandler() {
    override fun perform(session: XDebugSession, dataContext: DataContext) {
      (session.debugProcess as? PyStepOutOfBlockSupport)?.performStepOutOfBlock()
    }

    override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean =
      super.isEnabled(session, dataContext) && session.debugProcess is PyStepOutOfBlockSupport
  }

  override fun getHandler(): DebuggerActionHandler = handler

  override fun isHidden(event: AnActionEvent): Boolean {
    val session = event.getData(XDebugSession.DATA_KEY)
                  ?: event.project?.let { XDebuggerManager.getInstance(it).currentSession }
                  ?: return true
    return session.debugProcess !is PyStepOutOfBlockSupport
  }
}

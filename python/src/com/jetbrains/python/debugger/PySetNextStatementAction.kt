// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler
import com.jetbrains.python.debugger.pydev.PyDebugCallback

class PySetNextStatementAction : XDebuggerActionBase(true) {
  private val setNextStatementActionHandler: XDebuggerSuspendedActionHandler

  init {
    setNextStatementActionHandler = object : XDebuggerSuspendedActionHandler() {
      override fun perform(session: XDebugSession, dataContext: DataContext) {
        val debugProcess = session.debugProcess as? PyDebugProcess ?: return
        val position = XDebuggerUtilImpl.getCaretPosition(session.project, dataContext) ?: return
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: FileEditorManager.getInstance(session.project).selectedTextEditor
        val suspendContext = debugProcess.session.suspendContext
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
          debugProcess.startSetNextStatement(suspendContext, position, object : PyDebugCallback<Pair<Boolean, String>> {
            override fun ok(response: Pair<Boolean, String>) {
              if (!response.first && editor != null) {
                ApplicationManager.getApplication().invokeLater(Runnable {
                  if (!editor.isDisposed) {
                    editor.caretModel.moveToOffset(position.offset)
                    HintManager.getInstance().showErrorHint(editor, response.second) // NON-NLS
                  }
                }, ModalityState.defaultModalityState())
              }
            }

            override fun error(e: PyDebuggerException) {
              LOG.error(e)
            }
          })
        })
      }

      override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
        return super.isEnabled(project, event) && PyDebugSupportUtils.isCurrentPythonDebugProcess(event)
      }
    }
  }

  override fun getHandler(debuggerSupport: DebuggerSupport): XDebuggerSuspendedActionHandler = setNextStatementActionHandler

  override fun isHidden(event: AnActionEvent): Boolean {
    val project = event.getData(CommonDataKeys.PROJECT)
    return project == null || !PyDebugSupportUtils.isCurrentPythonDebugProcess(event)
  }

  companion object {
    private val LOG = Logger.getInstance(PySetNextStatementAction::class.java)
  }
}

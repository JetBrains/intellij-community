/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
                    HintManager.getInstance().showErrorHint(editor, response.second)
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
        return super.isEnabled(project, event) && PyDebugSupportUtils.isCurrentPythonDebugProcess(project)
      }
    }
  }

  override fun getHandler(debuggerSupport: DebuggerSupport) = setNextStatementActionHandler

  override fun isHidden(event: AnActionEvent): Boolean {
    val project = event.getData(CommonDataKeys.PROJECT)
    return project == null || !PyDebugSupportUtils.isCurrentPythonDebugProcess(project)
  }

  companion object {
    private val LOG = Logger.getInstance("#com.jetbrains.python.debugger.PySetNextStatementAction")
  }
}

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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler
import com.jetbrains.python.debugger.pydev.PyDebugCallback

class PySetNextStatementAction : XDebuggerActionBase() {
  private val LOG = Logger.getInstance("#com.jetbrains.python.debugger.PySetNextStatementAction")
  private val mySetNextStatementActionHandler: XDebuggerSuspendedActionHandler

  init {
    mySetNextStatementActionHandler = object : XDebuggerSuspendedActionHandler() {
      override fun perform(session: XDebugSession, dataContext: DataContext) {
        val debugProcess = session.debugProcess
        if (debugProcess is PyDebugProcess) {
          val position = XDebuggerUtilImpl.getCaretPosition(session.project, dataContext) ?: return
          val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: FileEditorManager.getInstance(session.project).selectedTextEditor
          debugProcess
            .startSetNextStatement(debugProcess.getSession().suspendContext, position, object : PyDebugCallback<Pair<Boolean, String>> {
              override fun ok(response: Pair<Boolean, String>) {
                if (!response.first && editor != null) {
                  UIUtil.invokeLaterIfNeeded {
                    editor.caretModel.moveToOffset(position.offset)
                    HintManager.getInstance().showErrorHint(editor, response.second)
                  }
                }
              }

              override fun error(e: PyDebuggerException) {
                LOG.error(e)
              }
            })
        }
      }

      override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
        return super.isEnabled(project, event) && PyDebugSupportUtils.isPythonConfigurationSelected(project)
      }
    }
  }

  override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    return mySetNextStatementActionHandler
  }

  override fun isHidden(event: AnActionEvent): Boolean {
    return !PyDebugSupportUtils.isPythonConfigurationSelected(event.getData(CommonDataKeys.PROJECT))
  }
}

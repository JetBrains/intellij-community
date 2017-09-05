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
package com.jetbrains.python.debugger;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PySetNextStatementAction extends XDebuggerActionBase {
  private final XDebuggerSuspendedActionHandler mySetNextStatementActionHandler;

  public PySetNextStatementAction() {

    mySetNextStatementActionHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
        final XDebugProcess debugProcess = session.getDebugProcess();
        if (debugProcess instanceof PyDebugProcess) {
          PyDebugProcess pyDebugProcess = (PyDebugProcess)debugProcess;
          XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext);
          Editor editor = getEditor(session.getProject(), dataContext);
          if (position != null) {
            pyDebugProcess.startSetNextStatement(debugProcess.getSession().getSuspendContext(), position, editor);
          }
        }
      }

      @Override
      public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
        return super.isEnabled(project, event) && PyDebugSupportUtils.isPythonConfigurationSelected(project);
      }
    };
  }

  @Nullable
  private static Editor getEditor(@NotNull Project project, DataContext context) {
    Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor == null) {
      return FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    return editor;
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return mySetNextStatementActionHandler;
  }

  @Override
  protected boolean isHidden(AnActionEvent event) {
    return !PyDebugSupportUtils.isPythonConfigurationSelected(event.getData(CommonDataKeys.PROJECT));
  }
}

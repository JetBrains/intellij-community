/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import org.jetbrains.annotations.NotNull;


public class PyStepIntoMyCodeAction extends XDebuggerActionBase {
  private final XDebuggerSuspendedActionHandler myStepIntoMyCodeHandler;

  public PyStepIntoMyCodeAction() {
    super();
    myStepIntoMyCodeHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        final XDebugProcess debugProcess = session.getDebugProcess();
        if (debugProcess instanceof PyDebugProcess) {
          PyDebugProcess pyDebugProcess = (PyDebugProcess)debugProcess;
          pyDebugProcess.startStepIntoMyCode(debugProcess.getSession().getSuspendContext());
        }
      }

      @Override
      public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
        return super.isEnabled(project, event) && PyDebugSupportUtils.isCurrentPythonDebugProcess(project);
      }
    };
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return myStepIntoMyCodeHandler;
  }

  @Override
  protected boolean isHidden(AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    return project == null || !PyDebugSupportUtils.isCurrentPythonDebugProcess(project);
  }
}

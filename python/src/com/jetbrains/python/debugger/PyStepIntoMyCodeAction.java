// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        return super.isEnabled(project, event) && PyDebugSupportUtils.isCurrentPythonDebugProcess(event);
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
    return project == null || !PyDebugSupportUtils.isCurrentPythonDebugProcess(event);
  }
}

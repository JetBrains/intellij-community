// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        XDebugProcess debugProcess = session.getDebugProcess();
        if (debugProcess instanceof PyDebugProcess pyDebugProcess) {
          pyDebugProcess.startStepIntoMyCode(debugProcess.getSession().getSuspendContext());
        }
      }

      @Override
      public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
        return super.isEnabled(project, event) && PyDebugSupportUtils.isCurrentPythonDebugProcess(event);
      }
    };
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return myStepIntoMyCodeHandler;
  }

  @Override
  protected boolean isHidden(@NotNull AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    return project == null || !PyDebugSupportUtils.isCurrentPythonDebugProcess(event);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.ForceStepIntoAction;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

public class PyForceStepIntoAction extends ForceStepIntoAction {
  private final XDebuggerSuspendedActionHandler myPyForceStepIntoHandler;

  public PyForceStepIntoAction() {
    myPyForceStepIntoHandler = new XDebuggerSuspendedActionHandler() {
      @Override
      protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        session.forceStepInto();
      }

      private static boolean isPythonConfig(DataContext dataContext) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) return false;
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
        if (settings != null) {
          return settings.getConfiguration() instanceof AbstractPythonRunConfiguration;
        }
        return false;
      }

      @Override
      public boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
        if (isPythonConfig(dataContext)) {
          return false;
        }
        return super.isEnabled(session, dataContext);
      }

      @Override
      public boolean isHidden(@NotNull Project project, @NotNull AnActionEvent event) {
        if (isPythonConfig(event.getDataContext())) {
          return true;
        }
        return super.isHidden(project, event);
      }
    };
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return myPyForceStepIntoHandler;
  }
}

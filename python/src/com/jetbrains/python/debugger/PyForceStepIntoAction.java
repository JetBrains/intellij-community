/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
      protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
        session.forceStepInto();
      }

      private boolean isPythonConfig(final DataContext dataContext) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project == null) return false;
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
        if (settings != null) {
          return settings.getConfiguration() instanceof AbstractPythonRunConfiguration;
        }
        return false;
      }

      @Override
      public boolean isEnabled(@NotNull XDebugSession session, final DataContext dataContext) {
        if (isPythonConfig(dataContext)) {
          return false;
        }
        return super.isEnabled(session, dataContext);
      }

      @Override
      public boolean isHidden(@NotNull Project project, AnActionEvent event) {
        if (isPythonConfig(event.getDataContext())) {
          return true;
        }
        return super.isHidden(project, event);
      }
    };
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return myPyForceStepIntoHandler;
  }
}

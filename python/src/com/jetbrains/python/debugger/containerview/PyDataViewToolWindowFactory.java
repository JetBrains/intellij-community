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
package com.jetbrains.python.debugger.containerview;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.jetbrains.python.console.PythonConsoleToolWindowFactory;
import com.jetbrains.python.debugger.PyDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyDataViewToolWindowFactory implements ToolWindowFactory {
  public static final String EMPTY_TEXT = "Run debugger to view available data ";
  private static final Logger LOG = Logger.getInstance(PyDataViewToolWindowFactory.class);

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    PyDataView.getInstance(project).init(toolWindow);
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new ChangeContentXDebuggerManagerListener(project));

    connection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (!(executor instanceof DefaultDebugExecutor)) {
          return;
        }
        if (descriptor == null) {
          return;
        }
        ProcessHandler handler = descriptor.getProcessHandler();
        if (handler == null) {
          return;
        }
        PyDataView.getInstance(project).updateTabs(handler);
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {

      }
    });

    addPythonConsoleListener(project);
    ((ToolWindowEx)toolWindow).setAdditionalGearActions(new DefaultActionGroup(new ColoredByDefaultAction()));
  }

  private static void addPythonConsoleListener(@NotNull Project project) {
    final ToolWindow pythonConsole = ToolWindowManager.getInstance(project).getToolWindow(PythonConsoleToolWindowFactory.Companion.getID());
    if (pythonConsole == null) {
      return;
    }
    pythonConsole.getContentManager().addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        PyDataView.getInstance(project).closeDisconnectedFromConsoleTabs();
      }
    });
  }

  private static class ColoredByDefaultAction extends ToggleAction {
    public ColoredByDefaultAction() {
      super("Colored by Default");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return PropertiesComponent.getInstance(e.getProject()).getBoolean(PyDataView.COLORED_BY_DEFAULT, true);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(e.getProject()).setValue(PyDataView.COLORED_BY_DEFAULT, state, true);
    }
  }

  private static class ChangeContentXDebuggerManagerListener implements XDebuggerManagerListener {
    private final Project myProject;

    public ChangeContentXDebuggerManagerListener(Project project) {
      myProject = project;
    }

    @Override
    public void processStarted(@NotNull XDebugProcess debugProcess) {

    }

    @Override
    public void processStopped(@NotNull XDebugProcess debugProcess) {
      if (debugProcess instanceof PyDebugProcess) {
        PyDataView.getInstance(myProject).closeTabs(frameAccessor -> frameAccessor instanceof PyDebugProcess && frameAccessor == debugProcess);
      }
    }
  }
}

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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PyDataViewToolWindowFactory implements ToolWindowFactory {
  private JBLabel myEmptyContent = new JBLabel("Run debugger to view available data ", SwingConstants.CENTER);
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      createEmptyContent(toolWindow);
    }
    else {
      PyDataView.getInstance(project).init(toolWindow, session.getDebugProcess());
    }
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override
      public void processStarted(@NotNull XDebugProcess debugProcess) {
        toolWindow.getContentManager().removeAllContents(true);
        PyDataView.getInstance(project).init(toolWindow, debugProcess);
      }

      @Override
      public void processStopped(@NotNull XDebugProcess debugProcess) {
        createEmptyContent(toolWindow);
      }
    });

    ((ToolWindowEx)toolWindow).setAdditionalGearActions(new DefaultActionGroup(new ColoredByDefaultAction()));
  }

  private void createEmptyContent(@NotNull ToolWindow toolWindow) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ContentManager manager = toolWindow.getContentManager();
      manager.removeAllContents(true);
      manager.addContent(ContentFactory.SERVICE.getInstance().createContent(myEmptyContent, "", false));
    });
  }

  @Override
  public void init(ToolWindow window) {
    window.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.FLOATING, null);
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
}

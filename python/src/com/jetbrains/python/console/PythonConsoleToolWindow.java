/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

/**
 * @author traff
 */
public class PythonConsoleToolWindow {
  public static final Key<RunContentDescriptor> CONTENT_DESCRIPTOR = Key.create("CONTENT_DESCRIPTOR");

  public static final Function<Content, RunContentDescriptor>
    CONTENT_TO_DESCRIPTOR_FUNCTION = new Function<Content, RunContentDescriptor>() {
    @Override
    public RunContentDescriptor apply(@Nullable Content input) {
      return input != null ? input.getUserData(CONTENT_DESCRIPTOR) : null;
    }
  };

  private final Project myProject;

  private boolean myInitialized = false;

  public PythonConsoleToolWindow(Project project) {
    myProject = project;
  }

  public static PythonConsoleToolWindow getInstance(@NotNull Project project) {
    return project.getComponent(PythonConsoleToolWindow.class);
  }

  public List<RunContentDescriptor> getConsoleContentDescriptors() {
    return FluentIterable.from(Lists.newArrayList(getToolWindow(myProject).getContentManager().getContents()))
      .transform(CONTENT_TO_DESCRIPTOR_FUNCTION).filter(
        Predicates.notNull()).toList();
  }


  public void init(final @NotNull ToolWindow toolWindow, final @NotNull RunContentDescriptor contentDescriptor) {
    setContent(toolWindow, contentDescriptor);

    if (!myInitialized) {
      doInit(toolWindow);
    }
  }

  private void doInit(@NotNull final ToolWindow toolWindow) {
    myInitialized = true;

    toolWindow.setToHideOnEmptyContent(true);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = getToolWindow(myProject);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible && toolWindow.getContentManager().getContentCount() == 0) {
            PydevConsoleRunner runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(myProject, null);
            runner.run();
          }
        }
      }
    });
  }

  private static void setContent(ToolWindow toolWindow, RunContentDescriptor contentDescriptor) {
    toolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");

    Content content = toolWindow.getContentManager().findContent(contentDescriptor.getDisplayName());
    if (content == null) {
      content = createContent(contentDescriptor);
      toolWindow.getContentManager().addContent(content);
    }
    else {
      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      resetContent(contentDescriptor, panel, content);
    }

    toolWindow.getContentManager().setSelectedContent(content);
  }

  public ToolWindow getToolWindow() {
    return getToolWindow(myProject);
  }

  public static ToolWindow getToolWindow(Project project) {
    return ToolWindowManager.getInstance(project).getToolWindow(PythonConsoleToolWindowFactory.Companion.getID());
  }

  public void setContent(RunContentDescriptor contentDescriptor) {
    setContent(getToolWindow(myProject), contentDescriptor);
  }

  private static Content createContent(final @NotNull RunContentDescriptor contentDescriptor) {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, contentDescriptor.getDisplayName(), false);
    content.setCloseable(true);

    resetContent(contentDescriptor, panel, content);

    return content;
  }

  private static void resetContent(RunContentDescriptor contentDescriptor, SimpleToolWindowPanel panel, Content content) {
    RunContentDescriptor oldDescriptor =
      content.getDisposer() instanceof RunContentDescriptor ? (RunContentDescriptor)content.getDisposer() : null;
    if (oldDescriptor != null) Disposer.dispose(oldDescriptor);

    panel.setContent(contentDescriptor.getComponent());

    content.setComponent(panel);
    content.setDisposer(contentDescriptor);
    content.setPreferredFocusableComponent(contentDescriptor.getComponent());

    content.putUserData(CONTENT_DESCRIPTOR, contentDescriptor);
  }

  private static FocusListener createFocusListener(final ToolWindow toolWindow) {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        JComponent component = getComponentToFocus(toolWindow);
        if (component != null) {
          component.requestFocusInWindow();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {

      }
    };
  }

  private static JComponent getComponentToFocus(ToolWindow window) {
    return window.getContentManager().getComponent();
  }


  public void activate(@NotNull Runnable runnable) {
    getToolWindow(myProject).activate(runnable);
  }

  @Nullable
  public RunContentDescriptor getSelectedContentDescriptor() {
    return CONTENT_TO_DESCRIPTOR_FUNCTION.apply(getToolWindow(myProject).getContentManager().getSelectedContent());
  }
}

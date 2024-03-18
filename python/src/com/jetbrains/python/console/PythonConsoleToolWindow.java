// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.icons.PythonIcons;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Service(Service.Level.PROJECT)
public final class PythonConsoleToolWindow {
  public static final Key<RunContentDescriptor> CONTENT_DESCRIPTOR = Key.create("CONTENT_DESCRIPTOR");

  public static final Function<Content, RunContentDescriptor> CONTENT_TO_DESCRIPTOR_FUNCTION = input -> {
    return input == null ? null : input.getUserData(CONTENT_DESCRIPTOR);
  };

  private final Project myProject;

  private boolean myInitialized = false;

  public PythonConsoleToolWindow(Project project) {
    myProject = project;
  }

  public static PythonConsoleToolWindow getInstance(@NotNull Project project) {
    return project.getService(PythonConsoleToolWindow.class);
  }

  public List<RunContentDescriptor> getConsoleContentDescriptors() {
    return FluentIterable.from(List.of(getToolWindow(myProject).getContentManager().getContents()))
      .transform(CONTENT_TO_DESCRIPTOR_FUNCTION)
      .filter(Predicates.notNull())
      .toList();
  }

  public void init(final @NotNull ToolWindow toolWindow, final @NotNull RunContentDescriptor contentDescriptor) {
    setContent(toolWindow, contentDescriptor);

    if (!myInitialized) {
      doInit(toolWindow);
    }

    if (toolWindow instanceof ToolWindowEx toolWindowEx) {
      toolWindowEx.setTabActions(new NewConsoleAction());
    }
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  private void doInit(final @NotNull ToolWindow toolWindow) {
    myInitialized = true;

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void toolWindowShown(@NotNull ToolWindow toolwindow) {
        ToolWindow window = getToolWindow(myProject);
        if (window.isVisible() && toolWindow.getContentManager().getContentCount() == 0) {
          PydevConsoleRunner runner = PythonConsoleRunnerFactory.getInstance().createConsoleRunner(myProject, null);
          runner.run(true);
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

  public @NotNull ToolWindow getToolWindow() {
    return getToolWindow(myProject);
  }

  public static @NotNull ToolWindow getToolWindow(@NotNull Project project) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow consoleToolWindow = toolWindowManager.getToolWindow(PythonConsoleToolWindowFactory.ID);
    if (consoleToolWindow == null) {
      consoleToolWindow = toolWindowManager.registerToolWindow(PythonConsoleToolWindowFactory.ID, builder -> {
        builder.hideOnEmptyContent = false;
        builder.anchor = ToolWindowAnchor.BOTTOM;
        return Unit.INSTANCE;
      });
      consoleToolWindow.setIcon(PythonIcons.Python.PythonConsoleToolWindow);
    }
    return consoleToolWindow;
  }

  public void setContent(RunContentDescriptor contentDescriptor) {
    setContent(getToolWindow(myProject), contentDescriptor);
  }

  private static Content createContent(final @NotNull RunContentDescriptor contentDescriptor) {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    Content content = ContentFactory.getInstance().createContent(panel, contentDescriptor.getDisplayName(), false);
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
    content.setPreferredFocusedComponent(contentDescriptor.getPreferredFocusComputable());

    content.putUserData(CONTENT_DESCRIPTOR, contentDescriptor);
  }

  public void activate(@NotNull Runnable runnable) {
    getToolWindow(myProject).activate(runnable);
  }

  public @Nullable RunContentDescriptor getSelectedContentDescriptor() {
    return CONTENT_TO_DESCRIPTOR_FUNCTION.apply(getToolWindow(myProject).getContentManager().getSelectedContent());
  }

  private static class NewConsoleAction extends AnAction implements DumbAware {
    NewConsoleAction() {
      super(PyBundle.messagePointer("console.new.console"), PyBundle.messagePointer("console.new.console.description"),
            AllIcons.General.Add);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        PydevConsoleRunner runner =
          PythonConsoleRunnerFactory.getInstance().createConsoleRunner(project, e.getData(PlatformCoreDataKeys.MODULE));
        runner.run(true);
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
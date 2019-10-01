// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class ShTerminalRunner extends ShRunner {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);

  protected ShTerminalRunner(@NotNull Project project) {
    super(project);
  }

  @Override
  public void run(@NotNull String command, @NotNull String workingDirectory) {
    TerminalView terminalView = TerminalView.getInstance(myProject);
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window == null) return;

    ContentManager contentManager = window.getContentManager();
    Pair<Content, ShellTerminalWidget> pair = getSuitableProcess(contentManager, workingDirectory);
    try {
      if (pair == null) {
        terminalView.createLocalShellWidget(workingDirectory).executeCommand(command);
        return;
      }
      window.activate(null);
      contentManager.setSelectedContent(pair.first);
      pair.second.executeCommand(command);
    } catch (IOException e) {
      LOG.warn("Cannot run command:" + command, e);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    return window != null && window.isAvailable();
  }

  @Nullable
  private static Pair<Content, ShellTerminalWidget> getSuitableProcess(@NotNull ContentManager contentManager, @NotNull String workingDirectory) {
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      Pair<Content, ShellTerminalWidget> pair = getSuitableProcess(selectedContent, workingDirectory);
      if (pair != null) return pair;
    }

    return Arrays.stream(contentManager.getContents())
      .map(content -> getSuitableProcess(content, workingDirectory))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static Pair<Content, ShellTerminalWidget> getSuitableProcess(@NotNull Content content, @NotNull String workingDirectory) {
    JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
    if (!(widget instanceof ShellTerminalWidget)) return null;
    ShellTerminalWidget shellTerminalWidget = (ShellTerminalWidget)widget;
    if (!shellTerminalWidget.getTypedShellCommand().isEmpty() || shellTerminalWidget.hasRunningCommands()) return null;
    String currentWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(shellTerminalWidget, null);
    if (currentWorkingDirectory == null || !currentWorkingDirectory.equals(workingDirectory)) return null;
    return Pair.create(content, shellTerminalWidget);
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run.terminal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.sh.run.ShRunner;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.jediterm.terminal.ProcessTtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;
import org.jetbrains.plugins.terminal.TerminalUtil;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

import java.util.Arrays;
import java.util.Objects;

final class ShTerminalRunner implements ShRunner {

  @Override
  public void run(@NotNull Project project,
                  @NotNull String command,
                  @NotNull String workingDirectory,
                  @NotNull @NlsContexts.TabTitle String title,
                  boolean activateToolWindow) {
    TerminalToolWindowManager terminalToolWindowManager = TerminalToolWindowManager.getInstance(project);
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window == null) {
      return;
    }

    ContentManager contentManager = window.getContentManager();
    Pair<Content, TerminalWidget> pair = getSuitableProcess(contentManager, workingDirectory);
    if (pair == null) {
      terminalToolWindowManager.createShellWidget(workingDirectory, title, activateToolWindow, activateToolWindow)
        .sendCommandToExecute(command);
      return;
    }
    if (activateToolWindow) {
      window.activate(null);
    }
    pair.first.setDisplayName(title);
    contentManager.setSelectedContent(pair.first);
    pair.second.sendCommandToExecute(command);
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    return window != null && window.isAvailable();
  }

  private static @Nullable Pair<Content, TerminalWidget> getSuitableProcess(@NotNull ContentManager contentManager,
                                                                            @NotNull String workingDirectory) {
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      Pair<Content, TerminalWidget> pair = getSuitableProcess(selectedContent, workingDirectory);
      if (pair != null) return pair;
    }

    return Arrays.stream(contentManager.getContents())
      .map(content -> getSuitableProcess(content, workingDirectory))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  private static @Nullable Pair<Content, TerminalWidget> getSuitableProcess(@NotNull Content content,
                                                                            @NotNull String workingDirectory) {
    TerminalWidget widget = TerminalToolWindowManager.findWidgetByContent(content);
    if (widget == null || (widget instanceof JBTerminalWidget && !(widget instanceof ShellTerminalWidget))) {
      return null;
    }

    if (widget instanceof ShellTerminalWidget shellTerminalWidget && !shellTerminalWidget.getTypedShellCommand().isEmpty()) {
      return null;
    }

    ProcessTtyConnector processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(widget.getTtyConnector());
    if (processTtyConnector == null || TerminalUtil.hasRunningCommands(processTtyConnector)) {
      return null;
    }

    String currentWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(widget);
    if (!FileUtil.pathsEqual(workingDirectory, currentWorkingDirectory)) {
      return null;
    }

    return new Pair<>(content, widget);
  }
}

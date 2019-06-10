// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalUtil;
import org.jetbrains.plugins.terminal.TerminalView;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class ShTerminalRunner extends ShRunner {
  private static final Logger LOG = Logger.getInstance(LocalTerminalDirectRunner.class);

  protected ShTerminalRunner(@NotNull Project project) {
    super(project);
  }

  @Override
  public void run(@NotNull String command) {
    TerminalView terminalView = TerminalView.getInstance(myProject);
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window == null) return;

    ContentManager contentManager = window.getContentManager();
    Pair<Content, Process> pair = getSuitableProcess(contentManager);
    if (pair != null) {
      try {
        window.activate(null);
        contentManager.setSelectedContent(pair.first);
        runCommand(pair.second, command);
      }
      catch (ExecutionException e) {
        LOG.warn("Error running terminal", e);
      }
    }
    else {
      terminalView.createNewSession(new LocalTerminalDirectRunner(myProject) {
        @Override
        protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
          PtyProcess process = super.createProcess(directory, commandHistoryFilePath);
          runCommand(process, command);
          return process;
        }
      });
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    return window != null && window.isAvailable();
  }

  @Nullable
  private static Pair<Content, Process> getSuitableProcess(@NotNull ContentManager contentManager) {
    Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null) {
      Pair<Content, Process> pair = getSuitableProcess(selectedContent);
      if (pair != null) return pair;
    }

    return Arrays.stream(contentManager.getContents())
      .map(ShTerminalRunner::getSuitableProcess)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static Pair<Content, Process> getSuitableProcess(@NotNull Content content) {
    JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
    if (widget == null) return null;
    if (widget.getTtyConnector() instanceof ProcessTtyConnector) {
      ProcessTtyConnector ttyConnector = (ProcessTtyConnector)widget.getTtyConnector();
      if (!TerminalUtil.hasRunningCommands(ttyConnector)) {
        return Pair.create(content, ttyConnector.getProcess());
      }
    }
    return null;
  }

  private static void runCommand(@NotNull Process process, @Nullable String command)
    throws ExecutionException {
    if (command != null) {
      try {
        process.getOutputStream().write(command.getBytes(CharsetToolkit.UTF8_CHARSET));
      }
      catch (IOException ex) {
        throw new ExecutionException("Fail to start " + command, ex);
      }
    }
    else {
      throw new ExecutionException("Cannot run command:" + command, null);
    }
  }
}

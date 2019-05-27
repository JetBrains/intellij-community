// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.sh.psi.ShFile;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ShTerminalRunner extends ShRunner {
  @Override
  public void run(@NotNull ShFile file) {
    Project project = file.getProject();
    TerminalView terminalView = TerminalView.getInstance(file.getProject());
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      ((ToolWindowImpl) window).ensureContentInitialized();
      window.activate(null);
    }
    terminalView.createNewSession(new LocalTerminalDirectRunner(project) {
      @Override
      protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
        PtyProcess process = super.createProcess(directory, commandHistoryFilePath);
        Pair<String, String> fileCommand = createCommandLine(file);
        if (fileCommand.first != null) {
          try {
            process.getOutputStream().write(fileCommand.first.getBytes(CharsetToolkit.UTF8_CHARSET));
          }
          catch (IOException ex) {
            throw new ExecutionException("Fail to start " + fileCommand.first, ex);
          }
        }
        else {
          throw new ExecutionException(fileCommand.second, null);
        }
        return process;
      }
    });
  }

  @Override
  public boolean isAvailable(@NotNull Project project) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    return window != null && window.isAvailable();
  }

  @NotNull
  private static Pair<String, String> createCommandLine(@NotNull ShFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return Pair.create(null, "Cannot run " + file.getName());
    }
    if (!virtualFile.exists()) {
      return Pair.create(null, "File " + virtualFile.getPath() + " doesn't exist");
    }
    String filePath = virtualFile.getPath() + "\n";
    if (VfsUtilCore.virtualToIoFile(virtualFile).canExecute()) {
      return Pair.create(filePath, null);
    }
    String executable = ShRunner.getShebangExecutable(file);
    if (executable == null) {
      String shellPath = TerminalOptionsProvider.Companion.getInstance().getShellPath();
      File shellFile = new File(shellPath);
      if (shellFile.isAbsolute() && shellFile.canExecute()) {
        executable = shellPath;
      }
    }
    return executable != null ? Pair.create(executable + " " + filePath, null) : Pair.create(filePath, null);
  }
}

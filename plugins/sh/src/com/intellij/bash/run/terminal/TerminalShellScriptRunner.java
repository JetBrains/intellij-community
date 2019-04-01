package com.intellij.bash.run.terminal;

import com.intellij.bash.psi.BashFile;
import com.intellij.bash.run.ShellScriptRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
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

public class TerminalShellScriptRunner extends ShellScriptRunner {

  @Override
  public void run(@NotNull BashFile bashFile) {
    Project project = bashFile.getProject();
    TerminalView terminalView = TerminalView.getInstance(bashFile.getProject());
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
    if (window != null && window.isAvailable()) {
      ((ToolWindowImpl) window).ensureContentInitialized();
      window.activate(null);
    }
    terminalView.createNewSession(new LocalTerminalDirectRunner(project) {
      @Override
      protected PtyProcess createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
        PtyProcess process = super.createProcess(directory, commandHistoryFilePath);
        Pair<String, String> fileCommand = createCommandLine(bashFile);
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
  private Pair<String, String> createCommandLine(@NotNull BashFile bashFile) {
    VirtualFile virtualFile = bashFile.getVirtualFile();
    if (virtualFile == null) {
      return Pair.create(null, "Cannot run " + bashFile.getName());
    }
    if (!virtualFile.exists()) {
      return Pair.create(null, "File " + virtualFile.getPath() + " doesn't exist");
    }
    String filePath = virtualFile.getPath() + "\n";
    if (VfsUtil.virtualToIoFile(virtualFile).canExecute()) {
      return Pair.create(filePath, null);
    }
    String executable = ShellScriptRunner.getShebangExecutable(bashFile);
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

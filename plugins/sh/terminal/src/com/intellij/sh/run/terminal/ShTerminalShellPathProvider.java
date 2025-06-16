package com.intellij.sh.run.terminal;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.sh.run.ShDefaultShellPathProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider;

import java.nio.file.Files;
import java.nio.file.Path;

final class ShTerminalShellPathProvider implements ShDefaultShellPathProvider {
  private final Project myProject;

  ShTerminalShellPathProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getDefaultShell() {
    TerminalProjectOptionsProvider terminalProjectOptionsProvider = TerminalProjectOptionsProvider.getInstance(myProject);
    String shellPathWithoutDefault = terminalProjectOptionsProvider.getShellPathWithoutDefault$intellij_terminal();
    if (shellPathWithoutDefault != null) {
      return shellPathWithoutDefault;
    }
    return findDefaultShellPath();
  }

  private static @NotNull String findDefaultShellPath() {
    if (SystemInfo.isWindows) {
      return "powershell.exe";
    }
    String shell = System.getenv("SHELL");
    Path shellPath = shell != null ? NioFiles.toPath(shell) : null;
    if (shellPath != null && Files.exists(shellPath)) {
      return shell;
    }
    Path bashPath = NioFiles.toPath("/bin/bash");
    if (bashPath != null && Files.exists(bashPath)) {
      return bashPath.toString();
    }
    return "/bin/sh";
  }

}

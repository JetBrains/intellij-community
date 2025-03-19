package com.intellij.sh.run.terminal;

import com.intellij.openapi.project.Project;
import com.intellij.sh.run.ShDefaultShellPathProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider;

public final class ShTerminalShellPathProvider implements ShDefaultShellPathProvider {
  private final Project myProject;

  public ShTerminalShellPathProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getDefaultShell() {
    TerminalProjectOptionsProvider terminalProjectOptionsProvider = TerminalProjectOptionsProvider.getInstance(myProject);
    return terminalProjectOptionsProvider.getShellPath();
  }
}

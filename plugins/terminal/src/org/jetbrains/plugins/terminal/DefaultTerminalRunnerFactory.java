package org.jetbrains.plugins.terminal;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class DefaultTerminalRunnerFactory {
  public @NotNull AbstractTerminalRunner<?> create(@NotNull Project project) {
    return new LocalBlockTerminalRunner(project);
  }
}

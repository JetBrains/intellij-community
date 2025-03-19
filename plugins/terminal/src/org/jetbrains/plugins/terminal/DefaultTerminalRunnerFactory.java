package org.jetbrains.plugins.terminal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class DefaultTerminalRunnerFactory {
  public @NotNull AbstractTerminalRunner<?> create(@NotNull Project project) {
    return new LocalBlockTerminalRunner(project);
  }

  /**
   * @return the runner, that will run the terminal process locally on this machine
   */
  @ApiStatus.Experimental
  public @NotNull AbstractTerminalRunner<?> createLocalRunner(@NotNull Project project) {
    return create(project);
  }

  public static @NotNull DefaultTerminalRunnerFactory getInstance() {
    return ApplicationManager.getApplication().getService(DefaultTerminalRunnerFactory.class);
  }
}

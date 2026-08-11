package org.jetbrains.plugins.terminal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the default local terminal runner ({@link LocalTerminalDirectRunner}).
 *
 * @deprecated To get the runner actually used by the Classic Terminal (which may be customized, e.g., in Remote Development),
 * use {@link TerminalToolWindowManager#getTerminalRunner()}.
 */
@Deprecated
@Service
public final class DefaultTerminalRunnerFactory {
  public @NotNull AbstractTerminalRunner<?> create(@NotNull Project project) {
    return new LocalTerminalDirectRunner(project);
  }

  public static @NotNull DefaultTerminalRunnerFactory getInstance() {
    return ApplicationManager.getApplication().getService(DefaultTerminalRunnerFactory.class);
  }
}

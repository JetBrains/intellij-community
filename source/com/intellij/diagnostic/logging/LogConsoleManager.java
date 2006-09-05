package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.AdditionalTabComponentManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 01-Feb-2006
 */
public interface LogConsoleManager extends AdditionalTabComponentManager {
  void addLogConsole(final String path, final boolean skipContent, final Project project, final String name, RunConfigurationBase configurationBase);
  void removeLogConsole(final String path);
}

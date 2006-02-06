package com.intellij.diagnostic.logging;

import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 01-Feb-2006
 */
public interface LogConsoleManager {
  void addLogConsole(final String path, final boolean skipContent, final Project project, final String name);
  void removeLogConsole(final String path);
}

package com.jetbrains.python.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public interface PyConsoleOptionsProvider {
  ExtensionPointName<PyConsoleOptionsProvider> EP_NAME = ExtensionPointName.create("Pythonid.consoleOptionsProvider");

  boolean isApplicableTo(Project project);
  String getName();
  String getHelpTopic();
  PyConsoleOptions.PyConsoleSettings getSettings(Project project);
}

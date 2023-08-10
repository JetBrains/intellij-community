// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;


public interface PyConsoleOptionsProvider {
  ExtensionPointName<PyConsoleOptionsProvider> EP_NAME = ExtensionPointName.create("Pythonid.consoleOptionsProvider");

  boolean isApplicableTo(Project project);
  @NlsContexts.ConfigurableName
  String getName();
  String getHelpTopic();
  PyConsoleOptions.PyConsoleSettings getSettings(Project project);
}

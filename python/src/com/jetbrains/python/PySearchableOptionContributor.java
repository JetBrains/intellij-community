// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import org.jetbrains.annotations.NotNull;

final class PySearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    processIntegratedTools(processor);
    processProjectSettings(processor);
    processScientific(processor);
  }

  private static void processScientific(final @NotNull SearchableOptionProcessor processor) {
    String id = "PyScientificConfigurable";
    processor.addOptions("matplotlib", null, null, id, null, true);
  }

  private static void processProjectSettings(SearchableOptionProcessor processor) {
    String id = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable";
    processor.addOptions("Python interpreter", "Project interpreter", "Python interpreter",
                         id, "Project interpreter", true);

    processor.addOptions("pip package", "Project interpreter", "Project Interpreter Packages",
                         id, "Project interpreter", true);


    processor.addOptions("virtualenv venv", "Project interpreter", "Create VirtualEnv",
                         id, "Project interpreter", true);

  }

  private static void processIntegratedTools(SearchableOptionProcessor processor) {
    final String configurableId = "com.jetbrains.python.configuration.PyIntegratedToolsModulesConfigurable";
    final String displayName = "Python Integrated Tools";
    processor.addOptions("Package requirements file", displayName, "Package requirements file",
                         configurableId, displayName, true);
    processor.addOptions("Default test runner", displayName, "Default test runner",
                         configurableId, displayName, true);
    processor.addOptions("Docstring format", displayName, "Docstring format",
                         configurableId, displayName, true);
    processor.addOptions("Analyze Python code in docstrings", displayName, "Analyze Python code in docstrings",
                         configurableId, displayName, true);
    processor.addOptions("Sphinx working directory", displayName, "Sphinx working directory",
                         configurableId, displayName, true);
    processor.addOptions("Treat txt files as reStructuredText", displayName, "Treat *.txt files as reStructuredText ",
                         configurableId, displayName, false);
    processor.addOptions("reStructuredText", displayName, "Docstring format",
                         configurableId, displayName, false);
    processor.addOptions("Plain", displayName, "Docstring format",
                         configurableId, displayName, false);
    processor.addOptions("Unittests", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("pytest", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("Nosetests", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("Attests", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("Path to Pipenv executable", displayName, "Path to Pipenv executable",
                         configurableId, displayName, true);
  }
}

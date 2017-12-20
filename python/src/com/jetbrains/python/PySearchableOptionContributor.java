/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import org.jetbrains.annotations.NotNull;

public class PySearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    processIntegratedTools(processor);
    processProjectSettings(processor);
    processScientific(processor);
  }

  private static void processScientific(@NotNull final SearchableOptionProcessor processor) {
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
    processor.addOptions("Epytext", "Docstring format", "Docstring format",
                         configurableId, displayName, false);
    processor.addOptions("Plain", displayName, "Docstring format",
                         configurableId, displayName, false);
    processor.addOptions("Unittests", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("py.test", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("Nosetests", displayName, "Default test runner",
                         configurableId, displayName, false);
    processor.addOptions("Attests", displayName, "Default test runner",
                         configurableId, displayName, false);
  }
}

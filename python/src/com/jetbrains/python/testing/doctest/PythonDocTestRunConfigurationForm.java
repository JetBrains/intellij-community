/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.testing.doctest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PyTestSharedForm;
import com.jetbrains.python.testing.PythonTestLegacyRunConfigurationForm;

import javax.swing.*;
import java.awt.*;

public class PythonDocTestRunConfigurationForm implements PythonDocTestRunConfigurationParams {
  private JPanel myRootPanel;

  private final PythonTestLegacyRunConfigurationForm myTestRunConfigurationForm;


  public PythonDocTestRunConfigurationForm(final Project project, final PythonDocTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestLegacyRunConfigurationForm(project, configuration);
    PyTestSharedForm.setBorderToPanel(myTestRunConfigurationForm.getTestsPanel(), PyBundle.message("runcfg.doctest.display_name"));
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  public JComponent getPanel() {
    return myRootPanel;
  }
}



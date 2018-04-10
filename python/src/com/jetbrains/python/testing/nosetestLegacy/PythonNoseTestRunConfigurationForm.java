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
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestLegacyRunConfigurationForm;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class PythonNoseTestRunConfigurationForm implements PythonNoseTestRunConfigurationParams {
  private final JPanel myRootPanel;

  private final PythonTestLegacyRunConfigurationForm myTestRunConfigurationForm;

  public PythonNoseTestRunConfigurationForm(final Project project, final PythonNoseTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestLegacyRunConfigurationForm(project, configuration);
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
    myTestRunConfigurationForm.getPatternComponent().setVisible(false);
    TitledBorder border = (TitledBorder)myTestRunConfigurationForm.getTestsPanel().getBorder();
    border.setTitle(PyBundle.message("runcfg.nosetests.display_name"));
    myTestRunConfigurationForm.setParamsVisible();

    myTestRunConfigurationForm.getParamCheckBox().setSelected(configuration.useParam());
    myTestRunConfigurationForm.setPatternVisible(false);

  }

  public String getParams() {
    return myTestRunConfigurationForm.getParams();
  }

  public void setParams(String params) {
    myTestRunConfigurationForm.setParams(params);
  }

  @Override
  public boolean useParam() {
    return myTestRunConfigurationForm.getParamCheckBox().isSelected();
  }

  @Override
  public void useParam(boolean useParam) {
    myTestRunConfigurationForm.getParamCheckBox().setSelected(useParam);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

}



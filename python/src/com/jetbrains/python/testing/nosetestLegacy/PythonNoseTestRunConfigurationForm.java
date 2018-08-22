// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public String getParams() {
    return myTestRunConfigurationForm.getParams();
  }

  @Override
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



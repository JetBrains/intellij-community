package com.jetbrains.python.testing.nosetest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfigurationForm implements PythonNoseTestRunConfigurationParams {
  private JPanel myRootPanel;

  private final PythonTestRunConfigurationForm myTestRunConfigurationForm;

  public PythonNoseTestRunConfigurationForm(final Project project, final PythonNoseTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
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



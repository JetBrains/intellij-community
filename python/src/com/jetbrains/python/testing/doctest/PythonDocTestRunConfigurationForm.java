package com.jetbrains.python.testing.doctest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import java.awt.*;

/**
 * User: catherine
 */
public class PythonDocTestRunConfigurationForm implements PythonDocTestRunConfigurationParams {
  private JPanel myRootPanel;

  private final PythonTestRunConfigurationForm myTestRunConfigurationForm;


  public PythonDocTestRunConfigurationForm(final Project project, final PythonDocTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
  }
  public String getPattern() {
    return myTestRunConfigurationForm.getPattern();
  }

  public void setPattern(String pattern) {
    myTestRunConfigurationForm.setPattern(pattern);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  public JComponent getPanel() {
    return myRootPanel;
  }
}



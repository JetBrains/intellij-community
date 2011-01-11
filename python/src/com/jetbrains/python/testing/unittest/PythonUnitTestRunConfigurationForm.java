package com.jetbrains.python.testing.unittest;

import com.intellij.openapi.project.Project;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import java.awt.*;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfigurationForm implements PythonUnitTestRunConfigurationParams {
  private JPanel myTestsPlaceHolder;
  private JPanel myRootPanel;

  private final PythonTestRunConfigurationForm myTestRunConfigurationForm;


  public PythonUnitTestRunConfigurationForm(final Project project, final PythonUnitTestRunConfiguration configuration) {
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
    myTestsPlaceHolder.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
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



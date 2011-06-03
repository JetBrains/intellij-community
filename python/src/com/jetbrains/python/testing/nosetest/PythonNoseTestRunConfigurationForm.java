package com.jetbrains.python.testing.nosetest;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.jetbrains.python.testing.AbstractPythonTestRunConfigurationParams;
import com.jetbrains.python.testing.PythonTestRunConfigurationForm;

import javax.swing.*;
import java.awt.*;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfigurationForm implements PythonNoseTestRunConfigurationParams {
  private JPanel myRootPanel;

  private final PythonTestRunConfigurationForm myTestRunConfigurationForm;
  private JTextField myParamTextField;


  public PythonNoseTestRunConfigurationForm(final Project project, final PythonNoseTestRunConfiguration configuration) {
    myRootPanel = new JPanel(new BorderLayout());
    myTestRunConfigurationForm = new PythonTestRunConfigurationForm(project, configuration);
    myRootPanel.add(myTestRunConfigurationForm.getPanel(), BorderLayout.CENTER);
    myTestRunConfigurationForm.getAdditionalPanel().add(createParamComponent());
    myTestRunConfigurationForm.getPatternComponent().setVisible(false);
  }

  public String getParams() {
    return myParamTextField.getText().trim();
  }

  public void setParams(String params) {
    myParamTextField.setText(params);
  }

  @Override
  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return myTestRunConfigurationForm;
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  private LabeledComponent createParamComponent() {
    myParamTextField = new JTextField();

    LabeledComponent<JTextField> myComponent = new LabeledComponent<JTextField>();
    myComponent.setComponent(myParamTextField);
    myComponent.setText("Param");

    return myComponent;
  }
}



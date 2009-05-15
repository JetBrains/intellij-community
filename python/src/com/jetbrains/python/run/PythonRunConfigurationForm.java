package com.jetbrains.python.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams {
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myScriptTextField;
  private RawCommandLineEditor myScriptParametersTextField;
  private JPanel myCommonOptionsPlaceholder;
  private PyCommonOptionsForm myCommonOptionsForm;

  private final PythonRunConfiguration myConfiguration;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myConfiguration = configuration;
    myCommonOptionsForm = new PyCommonOptionsForm(configuration);
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

    Project project = myConfiguration.getProject();
    PythonRunConfigurationFormUtil.setupScriptField(project, myScriptTextField, myCommonOptionsForm.getWorkingDirectoryTextField());
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return myCommonOptionsForm;
  }

  public String getScriptName() {
    return toSystemIndependentName(myScriptTextField.getText().trim());
  }

  public void setScriptName(String scriptName) {
    myScriptTextField.setText(scriptName);
  }

  public String getScriptParameters() {
    return myScriptParametersTextField.getText().trim();
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParametersTextField.setText(scriptParameters);
  }
}

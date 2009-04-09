package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.RawCommandLineEditor;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams {
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myScriptTextField;
  private RawCommandLineEditor myScriptParametersTextField;
  private TextFieldWithBrowseButton myWorkingDirectoryTextField;
  private EnvironmentVariablesComponent myEnvsComponent;
  private JComboBox myInterpreterComboBox;

  private final PythonRunConfiguration myConfiguration;

  private RawCommandLineEditor myInterpreterOptionsTextField;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myConfiguration = configuration;

    initComponents();
  }

  private void initComponents() {
    Project project = myConfiguration.getProject();
    PythonRunConfigurationFormUtil
      .setupAbstractPythonRunConfigurationForm(myConfiguration.getProject(), myInterpreterComboBox, myWorkingDirectoryTextField);
    PythonRunConfigurationFormUtil.setupScriptField(project, myScriptTextField, myWorkingDirectoryTextField);
  }

  @NotNull
  protected JComponent createEditor() {
    return myRootPanel;
  }

  public JComponent getPanel() {
    return myRootPanel;
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

  public String getInterpreterOptions() {
    return myInterpreterOptionsTextField.getText().trim();
  }

  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptionsTextField.setText(interpreterOptions);
  }

  public String getWorkingDirectory() {
    return toSystemIndependentName(myWorkingDirectoryTextField.getText().trim());
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectoryTextField.setText(workingDirectory);
  }

  @Nullable
  public String getSdkHome() {
    Sdk selectedSdk = (Sdk)myInterpreterComboBox.getSelectedItem();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  public void setSdkHome(String sdkHome) {
    List<Sdk> sdkList = new ArrayList<Sdk>();
    sdkList.add(null);
    final List<Sdk> allSdks = PythonSdkType.getAllSdks();
    Sdk selection = null;
    for (Sdk sdk : allSdks) {
      if (FileUtil.pathsEqual(sdk.getHomePath(), sdkHome)) {
        selection = sdk;
        break;
      }
    }
    sdkList.addAll(allSdks);

    myInterpreterComboBox.setModel(new CollectionComboBoxModel(sdkList, selection));
  }

  public boolean isPassParentEnvs() {
    return myEnvsComponent.isPassParentEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    myEnvsComponent.setPassParentEnvs(passParentEnvs);
  }

  public Map<String, String> getEnvs() {
    return myEnvsComponent.getEnvs();
  }

  public void setEnvs(Map<String, String> envs) {
    myEnvsComponent.setEnvs(envs);
  }
}

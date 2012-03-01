package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private TextFieldWithBrowseButton myWorkingDirectoryTextField;
  private EnvironmentVariablesComponent myEnvsComponent;
  private RawCommandLineEditor myInterpreterOptionsTextField;
  private JComboBox myInterpreterComboBox;
  private JRadioButton myUseModuleSdkRadioButton;
  private JComboBox myModuleComboBox;
  private JPanel myMainPanel;
  private JRadioButton myUseSpecifiedSdkRadioButton;
  private JBLabel myPythonInterpreterJBLabel;
  private JBLabel myInterpreterOptionsJBLabel;
  private JBLabel myWorkingDirectoryJBLabel;
  private JComponent labelAnchor;

  public PyPluginCommonOptionsForm(PyCommonOptionsFormData data) {
    // setting modules
    final List<Module> validModules = data.getValidModules();
    Module selection = validModules.size() > 0 ? validModules.get(0) : null;
    myModuleComboBox.setModel(new CollectionComboBoxModel(validModules, selection));
    myModuleComboBox.setRenderer(new PyModuleRenderer(PyPluginCommonOptionsForm.this.myModuleComboBox.getRenderer()));

    myInterpreterComboBox.setRenderer(new SdkListCellRenderer("<Project Default>", myInterpreterComboBox.getRenderer()));
    myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", data.getProject(),
                                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myUseSpecifiedSdkRadioButton.addActionListener(listener);
    myUseModuleSdkRadioButton.addActionListener(listener);

    setAnchor(myEnvsComponent.getLabel());
  }

  private void updateControls() {
    myModuleComboBox.setEnabled(myUseModuleSdkRadioButton.isSelected());
    myInterpreterComboBox.setEnabled(myUseSpecifiedSdkRadioButton.isSelected());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @Override
  public void subscribe() {
  }

  @Override
  public void addInterpreterComboBoxActionListener(ActionListener listener) {
    myInterpreterComboBox.addActionListener(listener);
  }

  @Override
  public void removeInterpreterComboBoxActionListener(ActionListener listener) {
    myInterpreterComboBox.removeActionListener(listener);
  }

  public String getInterpreterOptions() {
    return myInterpreterOptionsTextField.getText().trim();
  }

  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptionsTextField.setText(interpreterOptions);
  }

  public String getWorkingDirectory() {
    return FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getText().trim());
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectoryTextField.setText(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
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
      String homePath = sdk.getHomePath();
      if (homePath != null && sdkHome != null && FileUtil.pathsEqual(homePath, sdkHome)) selection = sdk;
      sdkList.add(sdk);
    }

    myInterpreterComboBox.setModel(new CollectionComboBoxModel(sdkList, selection));
  }

  public Module getModule() {
    return (Module)myModuleComboBox.getSelectedItem();
  }

  public void setModule(Module module) {
    myModuleComboBox.setSelectedItem(module);
  }

  public boolean isUseModuleSdk() {
    return myUseModuleSdkRadioButton.isSelected();
  }

  public void setUseModuleSdk(boolean useModuleSdk) {
    if (useModuleSdk) {
      myUseModuleSdkRadioButton.setSelected(true);
    }
    else {
      myUseSpecifiedSdkRadioButton.setSelected(true);
    }
    updateControls();
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

  @Override
  public PyPathMappingSettings getMappingSettings() {
    return null;  //TODO: implement for plugin
  }

  @Override
  public void setMappingSettings(@Nullable PyPathMappingSettings mappingSettings) {
  }

  @Override
  public JComponent getAnchor() {
    return labelAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    labelAnchor = anchor;
    myPythonInterpreterJBLabel.setAnchor(anchor);
    myInterpreterOptionsJBLabel.setAnchor(anchor);
    myWorkingDirectoryJBLabel.setAnchor(anchor);
    myEnvsComponent.setAnchor(anchor);
  }
}

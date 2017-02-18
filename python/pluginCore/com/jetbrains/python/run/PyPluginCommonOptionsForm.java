/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.util.PathMappingsComponent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private final Project myProject;
  private TextFieldWithBrowseButton myWorkingDirectoryTextField;
  private EnvironmentVariablesComponent myEnvsComponent;
  private RawCommandLineEditor myInterpreterOptionsTextField;
  private ComboBox myInterpreterComboBox;
  private JRadioButton myUseModuleSdkRadioButton;
  private ModulesComboBox myModuleComboBox;
  private JPanel myMainPanel;
  private JRadioButton myUseSpecifiedSdkRadioButton;
  private JBLabel myPythonInterpreterJBLabel;
  private JBLabel myInterpreterOptionsJBLabel;
  private JBLabel myWorkingDirectoryJBLabel;
  private JPanel myHideablePanel;
  private PathMappingsComponent myPathMappingsComponent;
  private JBCheckBox myAddContentRootsCheckbox;
  private JBCheckBox myAddSourceRootsCheckbox;
  private JComponent labelAnchor;
  private final HideableDecorator myDecorator;

  public PyPluginCommonOptionsForm(PyCommonOptionsFormData data) {
    // setting modules
    myProject = data.getProject();
    final List<Module> validModules = data.getValidModules();
    Collections.sort(validModules, new ModulesAlphaComparator());
    Module selection = validModules.size() > 0 ? validModules.get(0) : null;
    myModuleComboBox.setModules(validModules);
    myModuleComboBox.setSelectedModule(selection);

    myInterpreterComboBox.setMinimumAndPreferredWidth(100);
    myInterpreterComboBox.setRenderer(new SdkListCellRenderer("<Project Default>"));
    myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", data.getProject(),
                                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myUseSpecifiedSdkRadioButton.addActionListener(listener);
    myUseModuleSdkRadioButton.addActionListener(listener);
    myInterpreterComboBox.addActionListener(listener);
    myModuleComboBox.addActionListener(listener);

    setAnchor(myEnvsComponent.getLabel());


    myDecorator = new HideableDecorator(myHideablePanel, "Environment", false) {
      @Override
      protected void on() {
        super.on();
        storeState();
      }

      @Override
      protected void off() {
        super.off();
        storeState();
      }
      private void storeState() {
        PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, String.valueOf(isExpanded()), "true");
      }
    };
    myDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
    myDecorator.setContentComponent(myMainPanel);
    myPathMappingsComponent.setAnchor(myEnvsComponent.getLabel());
    updateControls();
  }

  private void updateControls() {
    myModuleComboBox.setEnabled(myUseModuleSdkRadioButton.isSelected());
    myInterpreterComboBox.setEnabled(myUseSpecifiedSdkRadioButton.isSelected());
    myPathMappingsComponent.setVisible(PySdkUtil.isRemote(getSelectedSdk()));
  }

  public JPanel getMainPanel() {
    return myHideablePanel;
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
    List<Sdk> sdkList = new ArrayList<>();
    sdkList.add(null);
    final List<Sdk> allSdks = PythonSdkType.getAllSdks();
    Collections.sort(allSdks, new PreferredSdkComparator());
    Sdk selection = null;
    for (Sdk sdk : allSdks) {
      String homePath = sdk.getHomePath();
      if (homePath != null && sdkHome != null && FileUtil.pathsEqual(homePath, sdkHome)) selection = sdk;
      sdkList.add(sdk);
    }

    myInterpreterComboBox.setModel(new CollectionComboBoxModel(sdkList, selection));
  }

  public Module getModule() {
    return myModuleComboBox.getSelectedModule();
  }

  @Override
  public String getModuleName() {
    Module module = getModule();
    return module != null? module.getName() : null;
  }

  public void setModule(Module module) {
    myModuleComboBox.setSelectedModule(module);
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
  public PathMappingSettings getMappingSettings() {
    return myPathMappingsComponent.getMappingSettings();
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    myPathMappingsComponent.setMappingSettings(mappingSettings);
  }

  private Sdk getSelectedSdk() {
    if (isUseModuleSdk()) {
      Module module = getModule();
      return module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    }
    Sdk sdk = (Sdk)myInterpreterComboBox.getSelectedItem();
    if (sdk == null) {
      return ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
    return sdk;
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

  @Override
  public boolean shouldAddContentRoots() {
    return myAddContentRootsCheckbox.isSelected();
  }

  @Override
  public boolean shouldAddSourceRoots() {
    return myAddSourceRootsCheckbox.isSelected();
  }

  @Override
  public void setAddContentRoots(boolean flag) {
    myAddContentRootsCheckbox.setSelected(flag);
  }

  @Override
  public void setAddSourceRoots(boolean flag) {
    myAddSourceRootsCheckbox.setSelected(flag);
  }
}

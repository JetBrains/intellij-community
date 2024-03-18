// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private final Project myProject;
  private final PyPluginCommonOptionsPanel content;
  private JComponent labelAnchor;
  @NotNull
  private List<String> myEnvPaths = Collections.emptyList();

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();

  public PyPluginCommonOptionsForm(PyCommonOptionsFormData data) {
    // setting modules
    myProject = data.getProject();
    content = new PyPluginCommonOptionsPanel();
    final List<Module> validModules = data.getValidModules();
    validModules.sort(new ModulesAlphaComparator());
    Module selection = validModules.size() > 0 ? validModules.get(0) : null;
    content.moduleComboBox.setModules(validModules);
    content.moduleComboBox.setSelectedModule(selection);

    content.workingDirectoryTextField.addBrowseFolderListener(PyBundle.message("configurable.select.working.directory"), "", data.getProject(),
                                                              FileChooserDescriptorFactory.createSingleFolderDescriptor());

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    content.useSpecifiedSdkRadioButton.addActionListener(listener);
    content.useModuleSdkRadioButton.addActionListener(listener);
    content.interpreterComboBox.addActionListener(listener);
    content.moduleComboBox.addActionListener(listener);

    updateControls();

    addInterpreterComboBoxActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (Consumer<Boolean> f : myRemoteInterpreterModeListeners) {
          f.accept(PythonSdkUtil.isRemote(getSelectedSdk()));
        }
      }
    });
  }

  private void updateControls() {
    content.pathMappingsRow.visible(PythonSdkUtil.isRemote(getSelectedSdk()));
  }

  @Override
  public JPanel getMainPanel() {
    return content.panel;
  }

  @Override
  public void subscribe() {
  }

  @Override
  public void addInterpreterComboBoxActionListener(ActionListener listener) {
    content.interpreterComboBox.addActionListener(listener);
  }

  @Override
  public void removeInterpreterComboBoxActionListener(ActionListener listener) {
    content.interpreterComboBox.removeActionListener(listener);
  }

  @Override
  public void addInterpreterModeListener(Consumer<Boolean> listener) {
    myRemoteInterpreterModeListeners.add(listener);
  }

  @Override
  public String getInterpreterOptions() {
    return content.interpreterOptionsTextField.getText().trim();
  }

  @Override
  public void setInterpreterOptions(String interpreterOptions) {
    content.interpreterOptionsTextField.setText(interpreterOptions);
  }

  @Override
  public String getWorkingDirectory() {
    return FileUtil.toSystemIndependentName(content.workingDirectoryTextField.getText().trim());
  }

  @Override
  public void setWorkingDirectory(String workingDirectory) {
    content.workingDirectoryTextField.setText(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
  }

  @Override
  @Nullable
  public String getSdkHome() {
    Sdk selectedSdk = (Sdk)content.interpreterComboBox.getSelectedItem();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @Override
  public @Nullable Sdk getSdk() {
    return (Sdk)content.interpreterComboBox.getSelectedItem();
  }

  @Override
  public void setSdkHome(String sdkHome) {
    List<Sdk> sdkList = new ArrayList<>();
    sdkList.add(null);
    final List<Sdk> allSdks = ContainerUtil.sorted(PythonSdkUtil.getAllSdks(), new PreferredSdkComparator());
    Sdk selection = null;
    for (Sdk sdk : allSdks) {
      String homePath = sdk.getHomePath();
      if (homePath != null && sdkHome != null && FileUtil.pathsEqual(homePath, sdkHome)) selection = sdk;
      sdkList.add(sdk);
    }

    content.interpreterComboBox.setModel(new CollectionComboBoxModel<>(sdkList, selection));
  }

  @Override
  public void setSdk(@Nullable Sdk sdk) {
    List<Sdk> allSdks = PythonSdkUtil.getAllSdks();
    List<Sdk> sdkList = new ArrayList<>(allSdks);
    Sdk selection = null;
    for (Sdk curSdk: allSdks) {
      if (curSdk == sdk) {
        selection = curSdk;
      }
    }
    if (selection == null) {
      sdkList.add(sdk);
      selection = sdk;
    }
    content.interpreterComboBox.setModel(new CollectionComboBoxModel(sdkList, selection));
  }

  @Override
  public Module getModule() {
    return content.moduleComboBox.getSelectedModule();
  }

  @Override
  public String getModuleName() {
    Module module = getModule();
    return module != null ? module.getName() : null;
  }

  @Override
  public void setModule(Module module) {
    content.moduleComboBox.setSelectedModule(module);
  }

  @Override
  public boolean isUseModuleSdk() {
    return content.useModuleSdkRadioButton.isSelected();
  }

  @Override
  public void setUseModuleSdk(boolean useModuleSdk) {
    if (useModuleSdk) {
      content.useModuleSdkRadioButton.setSelected(true);
    }
    else {
      content.useSpecifiedSdkRadioButton.setSelected(true);
    }
    updateControls();
  }

  @Override
  public boolean isPassParentEnvs() {
    return content.envsComponent.isPassParentEnvs();
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    content.envsComponent.setPassParentEnvs(passParentEnvs);
  }

  @Override
  public Map<String, String> getEnvs() {
    return content.envsComponent.getEnvs();
  }

  @Override
  public void setEnvs(Map<String, String> envs) {
    content.envsComponent.setEnvs(envs);
  }

  @Override
  public PathMappingSettings getMappingSettings() {
    return content.pathMappingsComponent.getMappingSettings();
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    content.pathMappingsComponent.setMappingSettings(mappingSettings);
  }

  private Sdk getSelectedSdk() {
    if (isUseModuleSdk()) {
      Module module = getModule();
      return module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    }
    Sdk sdk = (Sdk)content.interpreterComboBox.getSelectedItem();
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
  }

  @Override
  public boolean shouldAddContentRoots() {
    return content.addContentRootsCheckbox.isSelected();
  }

  @Override
  public boolean shouldAddSourceRoots() {
    return content.addSourceRootsCheckbox.isSelected();
  }

  @Override
  public void setAddContentRoots(boolean flag) {
    content.addContentRootsCheckbox.setSelected(flag);
  }

  @Override
  public void setAddSourceRoots(boolean flag) {
    content.addSourceRootsCheckbox.setSelected(flag);
  }

  @NotNull
  @Override
  public List<String> getEnvFilePaths() {
    return myEnvPaths;
  }

  @Override
  public void setEnvFilePaths(@NotNull List<String> strings) {
    myEnvPaths = strings;
  }
}

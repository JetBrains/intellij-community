// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
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


public class PyIdeCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private final PyIdeCommonOptionsPanel content;
  private String mySelectedSdkHome = null;
  private Sdk mySelectedSdk = null;

  private JComponent labelAnchor;
  private final Project myProject;
  private List<Sdk> myPythonSdks;
  private @NotNull List<String> myEnvPaths = Collections.emptyList();
  private boolean myInterpreterRemote;

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();


  public PyIdeCommonOptionsForm(PyCommonOptionsFormData data) {
    myProject = data.getProject();
    myPythonSdks = new ArrayList<>(PythonSdkUtil.getAllSdks());
    myPythonSdks.add(0, null);
    Module[] modules = ModuleManager.getInstance(data.getProject()).getModules();
    boolean showModules = modules.length != 1;
    content = new PyIdeCommonOptionsPanel(data, showModules, myPythonSdks);
    content.workingDirectoryTextField.addBrowseFolderListener(data.getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(PyBundle.message("configurable.select.working.directory")));
    if (!showModules) {
      setModule(modules[0]);
    }
    else {
      final List<Module> validModules = data.getValidModules();
      Module selection = !validModules.isEmpty() ? validModules.get(0) : null;
      content.moduleCombo.setModules(validModules);
      content.moduleCombo.setSelectedModule(selection);
      content.moduleCombo.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateDefaultInterpreter(content.moduleCombo.getSelectedModule());
        }
      });
      updateDefaultInterpreter(content.moduleCombo.getSelectedModule());
    }

    addInterpreterComboBoxActionListener(new ActionListener() {
                                           @Override
                                           public void actionPerformed(ActionEvent event) {
                                             updateRemoteInterpreterMode();
                                           }
                                         }
    );

    updateRemoteInterpreterMode();

    addInterpreterModeListener((b) -> content.pathMappingsRow.visible(b));
  }

  @Override
  public JComponent getMainPanel() {
    return content.panel;
  }

  @Override
  public void subscribe() {
    PyConfigurableInterpreterList myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    ProjectSdksModel myProjectSdksModel = myInterpreterList.getModel();
    myProjectSdksModel.addListener(new MyListener(this, myInterpreterList));
    updateSdkList(true, myInterpreterList);
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
  public String getSdkHome() {
    Sdk selectedSdk = (Sdk)content.interpreterComboBox.getSelectedItem();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @Override
  public void setSdkHome(String sdkHome) {
    mySelectedSdkHome = sdkHome;
  }

  @Override
  public @Nullable Sdk getSdk() {
    return (Sdk)content.interpreterComboBox.getSelectedItem();
  }

  @Override
  public void setSdk(@Nullable Sdk sdk) {
    mySelectedSdk = sdk;
  }

  @Override
  public @Nullable Module getModule() {
    final Module selectedItem = content.moduleCombo.getSelectedModule();
    if (selectedItem != null) {
      return selectedItem;
    }
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length == 1) {
      return modules[0];
    }
    return null;
  }

  @Override
  public void setModule(Module module) {
    content.moduleCombo.setSelectedModule(module);
    updateDefaultInterpreter(module);
  }

  private void updateDefaultInterpreter(Module module) {
    final Sdk sdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    content.interpreterComboBox.setRenderer(
      sdk == null
      ? new PySdkListCellRenderer()
      : new PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.project.default.0", sdk.getName()), sdk)
    );
  }

  public void updateSdkList(boolean preserveSelection, PyConfigurableInterpreterList myInterpreterList) {
    myPythonSdks = myInterpreterList.getAllPythonSdks(myProject, null, false);
    Sdk selection = preserveSelection ? (Sdk)content.interpreterComboBox.getSelectedItem() : null;
    if (!myPythonSdks.contains(selection)) {
      selection = null;
    }
    myPythonSdks.add(0, null);
    content.interpreterComboBox.setModel(new CollectionComboBoxModel(myPythonSdks, selection));
  }

  @Override
  public boolean isUseModuleSdk() {
    return content.interpreterComboBox.getSelectedItem() == null;
  }

  @Override
  public void setUseModuleSdk(boolean useModuleSdk) {
    if (mySelectedSdk != null) {
      content.interpreterComboBox.setSelectedItem(useModuleSdk ? null : mySelectedSdk);
      return;
    }
    content.interpreterComboBox.setSelectedItem(useModuleSdk ? null : PythonSdkUtil.findSdkByPath(myPythonSdks, mySelectedSdkHome));
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
  public @Nullable PathMappingSettings getMappingSettings() {
    if (myInterpreterRemote) {
      return content.pathMappingsComponent.getMappingSettings();
    }
    else {
      return new PathMappingSettings();
    }
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    content.pathMappingsComponent.setMappingSettings(mappingSettings);
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

  private void setRemoteInterpreterMode(boolean isInterpreterRemote) {
    myInterpreterRemote = isInterpreterRemote;
  }

  private void updateRemoteInterpreterMode() {
    setRemoteInterpreterMode(PythonSdkUtil.isRemote(getSdkSelected()));
    for (Consumer<Boolean> f : myRemoteInterpreterModeListeners) {
      f.accept(myInterpreterRemote);
    }
  }

  private @Nullable Sdk getSdkSelected() {
    String sdkHome = getSdkHome();
    if (StringUtil.isEmptyOrSpaces(sdkHome)) {
      final Sdk projectJdk = PythonSdkUtil.findPythonSdk(getModule());
      if (projectJdk != null) {
        sdkHome = projectJdk.getHomePath();
      }
    }

    return PythonSdkUtil.findSdkByPath(sdkHome);
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
  public @NotNull List<String> getEnvFilePaths() {
    return myEnvPaths;
  }

  @Override
  public void setEnvFilePaths(@NotNull List<String> strings) {
    myEnvPaths = strings;
  }

  private static class MyListener implements SdkModel.Listener {
    private final PyIdeCommonOptionsForm myForm;
    private final PyConfigurableInterpreterList myInterpreterList;

    MyListener(PyIdeCommonOptionsForm form, PyConfigurableInterpreterList interpreterList) {
      myForm = form;
      myInterpreterList = interpreterList;
    }


    private void update() {
      myForm.updateSdkList(true, myInterpreterList);
    }

    @Override
    public void sdkAdded(@NotNull Sdk sdk) {
      update();
    }

    @Override
    public void beforeSdkRemove(@NotNull Sdk sdk) {
      update();
    }

    @Override
    public void sdkChanged(@NotNull Sdk sdk, String previousName) {
      update();
    }
  }

  @Override
  public String getModuleName() {
    Module module = getModule();
    return module != null ? module.getName() : null;
  }

  @Override
  public void addInterpreterModeListener(Consumer<Boolean> listener) {
    myRemoteInterpreterModeListeners.add(listener);
  }
}

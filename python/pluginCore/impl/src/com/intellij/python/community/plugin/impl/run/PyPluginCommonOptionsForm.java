// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.common.PyInterpreterItem;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PySdkRenderingKt;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


final class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private final Project myProject;
  private final PyPluginCommonOptionsPanel content;
  private JComponent labelAnchor;
  private @NotNull List<String> myEnvPaths = Collections.emptyList();

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();

  private static final Logger LOG = Logger.getInstance(PyPluginCommonOptionsForm.class);

  PyPluginCommonOptionsForm(PyCommonOptionsFormData data) {
    // setting modules
    myProject = data.getProject();
    content = new PyPluginCommonOptionsPanel();
    final List<Module> validModules = data.getValidModules();
    validModules.sort(new ModulesAlphaComparator());
    Module selection = !validModules.isEmpty() ? validModules.get(0) : null;
    content.moduleComboBox.setModules(validModules);
    content.moduleComboBox.setSelectedModule(selection);

    content.workingDirectoryTextField.addBrowseFolderListener(data.getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(PyBundle.message("configurable.select.working.directory")));

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
  public void subscribe(@NotNull Disposable parentDisposable) {
  }

  private void addInterpreterComboBoxActionListener(ActionListener listener) {
    content.interpreterComboBox.addActionListener(listener);
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
  public @Nullable String getSdkHome() {
    Sdk selectedSdk = getSdk();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @Override
  public @Nullable Sdk getSdk() {
    // The combo holds items, not SDKs. An item can outlive the interpreter it names, so this may be null.
    Object selected = content.interpreterComboBox.getSelectedItem();
    return selected instanceof PyInterpreterItem item ? PythonInterpreterExtKt.findSdk(item) : null;
  }

  @Override
  public void setSdkHome(String sdkHome) {
    final List<Sdk> allSdks = ContainerUtil.sorted(PythonSdkUtil.getAllSdks(), new PreferredSdkComparator());
    final List<PyInterpreterItem> allItems = PySdkRenderingKt.interpreterItemsUnderProgress(allSdks, myProject);
    List<PyInterpreterItem> rows = new ArrayList<>();
    rows.add(null);
    PyInterpreterItem selection = null;
    for (int i = 0; i < allSdks.size(); i++) {
      String homePath = allSdks.get(i).getHomePath();
      if (homePath != null && sdkHome != null && FileUtil.pathsEqual(homePath, sdkHome)) selection = allItems.get(i);
      rows.add(allItems.get(i));
    }

    content.interpreterComboBox.setModel(new CollectionComboBoxModel<>(rows, selection));
  }

  @Override
  public void setSdk(@Nullable Sdk sdk) {
    List<Sdk> allSdks = new ArrayList<>(PythonSdkUtil.getAllSdks());
    // An SDK the table does not hold is still offered, so the configuration keeps naming what it was given.
    if (sdk != null && !allSdks.contains(sdk)) {
      allSdks.add(sdk);
    }
    List<PyInterpreterItem> rows = PySdkRenderingKt.interpreterItemsUnderProgress(allSdks, myProject);
    PyInterpreterItem selection = null;
    for (int i = 0; i < allSdks.size(); i++) {
      if (allSdks.get(i) == sdk) selection = rows.get(i);
    }
    content.interpreterComboBox.setModel(new CollectionComboBoxModel<>(rows, selection));
  }

  @Override
  public Module getModule() {
    return content.moduleComboBox.getSelectedModule();
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Boolean getUseRunTool() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void setUseRunTool(@Nullable Boolean useRunTool) {

  }

  @ApiStatus.Internal
  @Override
  public @Nullable Boolean getRunAsScript() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void setRunAsScript(@Nullable Boolean runAsScript) {
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
    Sdk sdk = getSdk();
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

  @Override
  public void setDebugJustMyCode(boolean flag) {
    LOG.warn("Tried to set debugJustMyCode flag for common options form, which is an unsupported operation");
  }

  @Override
  public boolean shouldDebugJustMyCode() {
    return false;
  }

  @Override
  public @NotNull List<String> getEnvFilePaths() {
    return myEnvPaths;
  }

  @Override
  public void setEnvFilePaths(@NotNull List<String> strings) {
    myEnvPaths = strings;
  }
}

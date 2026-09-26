// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.python.sdk.common.PyInterpreterItem;
import com.intellij.python.sdk.common.PyInterpreterRef;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PySdkRenderingKt;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
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
  /** The combo's interpreter rows. The combo shows them after a leading null row for "project default". */
  private List<PyInterpreterItem> myInterpreterItems;
  private @NotNull List<String> myEnvPaths = Collections.emptyList();
  private boolean myInterpreterRemote;

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();

  private static final Logger LOG = Logger.getInstance(PyIdeCommonOptionsForm.class);

  public PyIdeCommonOptionsForm(PyCommonOptionsFormData data) {
    myProject = data.getProject();
    myInterpreterItems = PySdkRenderingKt.interpreterItemsUnderProgress(PythonSdkUtil.getAllSdks(), myProject);
    List<PyInterpreterItem> rows = new ArrayList<>(myInterpreterItems);
    rows.addFirst(null);
    Module[] modules = ModuleManager.getInstance(data.getProject()).getModules();
    boolean showModules = modules.length != 1;
    content = new PyIdeCommonOptionsPanel(data, showModules, rows);
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
  public void subscribe(@NotNull Disposable parentDisposable) {
    // Refresh the interpreter combo from the live SDK table whenever it changes. The connection is tied to
    // `parentDisposable`, so the listener does not outlive the owning UI.
    MessageBusConnection connection = myProject.getMessageBus().connect(parentDisposable);
    connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkAdded(@NotNull Sdk jdk) { updateSdkList(true); }

      @Override
      public void jdkRemoved(@NotNull Sdk jdk) { updateSdkList(true); }

      @Override
      public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) { updateSdkList(true); }
    });
    updateSdkList(true);
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
    Sdk selectedSdk = selectedSdk();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @Override
  public void setSdkHome(String sdkHome) {
    mySelectedSdkHome = sdkHome;
  }

  @Override
  public @Nullable Sdk getSdk() {
    return selectedSdk();
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
  public void setModule(Module module) {
    content.moduleCombo.setSelectedModule(module);
    updateDefaultInterpreter(module);
  }

  private void updateDefaultInterpreter(Module module) {
    final Sdk sdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    content.interpreterComboBox.setRenderer(
      sdk == null
      ? new PySdkListCellRenderer()
      : new PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.project.default.0", sdk.getName()),
                                  PySdkRenderingKt.interpreterItemsUnderProgress(List.of(sdk), content.panel).getFirst())
    );
  }

  public void updateSdkList(boolean preserveSelection) {
    myInterpreterItems = PySdkRenderingKt.interpreterItemsUnderProgress(PythonSdkUtil.getAllSdks(), content.panel);
    PyInterpreterItem selection =
      preserveSelection && content.interpreterComboBox.getSelectedItem() instanceof PyInterpreterItem item ? item : null;
    if (!myInterpreterItems.contains(selection)) {
      selection = null;
    }
    List<PyInterpreterItem> rows = new ArrayList<>(myInterpreterItems);
    rows.addFirst(null);
    content.interpreterComboBox.setModel(new CollectionComboBoxModel<>(rows, selection));
  }

  @Override
  public boolean isUseModuleSdk() {
    return content.interpreterComboBox.getSelectedItem() == null;
  }

  @Override
  public void setUseModuleSdk(boolean useModuleSdk) {
    if (mySelectedSdk != null) {
      content.interpreterComboBox.setSelectedItem(useModuleSdk ? null : itemFor(mySelectedSdk));
      return;
    }
    content.interpreterComboBox.setSelectedItem(
      useModuleSdk ? null : itemFor(PythonSdkUtil.findSdkByPath(mySelectedSdkHome)));
  }

  /** The SDK the combo has selected, or null for the "project default" row or an interpreter that is gone. */
  private @Nullable Sdk selectedSdk() {
    Object selected = content.interpreterComboBox.getSelectedItem();
    return selected instanceof PyInterpreterItem item ? PythonInterpreterExtKt.findSdk(item) : null;
  }

  /** The combo row that stands for {@code sdk}, or null when the combo holds no row for it. */
  private @Nullable PyInterpreterItem itemFor(@Nullable Sdk sdk) {
    if (sdk == null) return null;
    PyInterpreterRef ref = PythonInterpreterExtKt.asInterpreterRef(sdk);
    return ContainerUtil.find(myInterpreterItems, item -> ref.equals(item.getRef()));
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

  @Override
  public boolean shouldDebugJustMyCode() {
    return false;
  }

  @Override
  public void setDebugJustMyCode(boolean flag) {
    LOG.warn("Tried to set debugJustMyCode flag for common options form, which is an unsupported operation");
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

  private void addInterpreterComboBoxActionListener(ActionListener listener) {
    content.interpreterComboBox.addActionListener(listener);
  }

  @Override
  public @NotNull List<String> getEnvFilePaths() {
    return myEnvPaths;
  }

  @Override
  public void setEnvFilePaths(@NotNull List<String> strings) {
    myEnvPaths = strings;
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

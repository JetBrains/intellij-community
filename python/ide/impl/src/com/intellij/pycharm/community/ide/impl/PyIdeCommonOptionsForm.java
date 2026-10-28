// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.PythonInterpreterRegistry;
import com.intellij.python.sdk.common.PyInterpreterItem;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyModuleInterpreterReader;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.PyCommonOptionsFormData;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PySdkRenderingKt;
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
  /** The interpreter that {@link #setPythonInterpreter} gave, selected when the mode is not "project default". */
  private @Nullable PythonInterpreter mySelectedInterpreter = null;

  private JComponent labelAnchor;
  private final Project myProject;
  /** The interpreters of the combo, in the order of {@link #myInterpreterItems}. */
  private List<PythonInterpreter> myInterpreters;
  /** The combo's interpreter rows. The combo shows them after a leading null row for "project default". */
  private List<PyInterpreterItem> myInterpreterItems;
  private @NotNull List<String> myEnvPaths = Collections.emptyList();
  private boolean myInterpreterRemote;

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();

  private static final Logger LOG = Logger.getInstance(PyIdeCommonOptionsForm.class);

  public PyIdeCommonOptionsForm(PyCommonOptionsFormData data) {
    myProject = data.getProject();
    setInterpreters(PySdkRenderingKt.pyProjectInterpretersUnderProgress(myProject, null));
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
          updateSdkList(true);
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
    // Refresh the interpreter combo whenever an interpreter is added or removed. The listener is tied to
    // `parentDisposable`, so it does not outlive the owning UI.
    PythonInterpreterRegistry.Companion.getInstance(myProject).addTableChangeListener(parentDisposable, () -> updateSdkList(true));
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
  public @Nullable PythonInterpreter getPythonInterpreter() {
    return selectedInterpreter();
  }

  @Override
  public void setPythonInterpreter(@Nullable PythonInterpreter interpreter) {
    mySelectedInterpreter = interpreter;
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
    final PythonInterpreter interpreter = PyModuleInterpreterReader.findModuleInterpreterIfReady(module);
    final PyInterpreterItem item = interpreter != null ? PythonInterpreterExtKt.asItem(interpreter) : null;
    content.interpreterComboBox.setRenderer(
      item == null
      ? new PySdkListCellRenderer()
      : new PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.current"), item)
    );
  }

  public void updateSdkList(boolean preserveSelection) {
    setInterpreters(PySdkRenderingKt.pyProjectInterpretersUnderProgress(myProject, getModule()));
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
    content.interpreterComboBox.setSelectedItem(useModuleSdk ? null : itemFor(mySelectedInterpreter));
  }

  /** Makes {@code interpreters} the rows of the combo, see {@link #myInterpreters}. */
  private void setInterpreters(@NotNull List<PythonInterpreter> interpreters) {
    myInterpreters = interpreters;
    myInterpreterItems = ContainerUtil.map(interpreters, interpreter -> PythonInterpreterExtKt.asItem(interpreter));
  }

  /** The interpreter the combo has selected, or null for the "project default" row. */
  private @Nullable PythonInterpreter selectedInterpreter() {
    int index = myInterpreterItems.indexOf(content.interpreterComboBox.getSelectedItem());
    return index >= 0 ? myInterpreters.get(index) : null;
  }

  /** The combo row that stands for {@code interpreter}, or null when the combo holds no row for it. */
  private @Nullable PyInterpreterItem itemFor(@Nullable PythonInterpreter interpreter) {
    int index = interpreter != null ? myInterpreters.indexOf(interpreter) : -1;
    return index >= 0 ? myInterpreterItems.get(index) : null;
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
    setRemoteInterpreterMode(isRemote(selectedOrModuleInterpreter()));
    for (Consumer<Boolean> f : myRemoteInterpreterModeListeners) {
      f.accept(myInterpreterRemote);
    }
  }

  /** The selected interpreter, or the interpreter of the module for the "project default" row. */
  private @Nullable PythonInterpreter selectedOrModuleInterpreter() {
    final PythonInterpreter selected = selectedInterpreter();
    return selected != null ? selected : PyModuleInterpreterReader.findModuleInterpreterIfReady(getModule());
  }

  /** Whether {@code interpreter} runs on a target, which needs path mappings. */
  private static boolean isRemote(@Nullable PythonInterpreter interpreter) {
    return interpreter != null && PythonInterpreterExtKt.isOnTarget(interpreter);
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

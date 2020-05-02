// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.util.PathMappingsComponent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.configuration.PyConfigureInterpretersLinkPanel;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author yole
 */
public class PyIdeCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private JPanel myMainPanel;
  private EnvironmentVariablesComponent myEnvsComponent;
  private RawCommandLineEditor myInterpreterOptionsTextField;
  private TextFieldWithBrowseButton myWorkingDirectoryTextField;
  private JComboBox myInterpreterComboBox;
  private JBLabel myPythonInterpreterJBLabel;
  private JLabel myProjectLabel;
  private ModulesComboBox myModuleCombo;
  private JPanel myConfigureInterpretersPanel;
  private String mySelectedSdkHome = null;
  private PathMappingsComponent myPathMappingsComponent;
  private JPanel myHideablePanel;
  private JBCheckBox myAddContentRootsCheckbox;
  private JBCheckBox myAddSourceRootsCheckbox;

  private JComponent labelAnchor;
  private final Project myProject;
  private List<Sdk> myPythonSdks;
  private boolean myInterpreterRemote;

  private final List<Consumer<Boolean>> myRemoteInterpreterModeListeners = new ArrayList<>();


  public PyIdeCommonOptionsForm(PyCommonOptionsFormData data) {
    myProject = data.getProject();
    myWorkingDirectoryTextField.addBrowseFolderListener(PyBundle.message("configurable.select.working.directory"), "", data.getProject(),
                                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myPythonSdks = new ArrayList<>(PythonSdkUtil.getAllSdks());
    myPythonSdks.add(0, null);

    myInterpreterComboBox.setModel(new CollectionComboBoxModel(myPythonSdks, null));

    final Module[] modules = ModuleManager.getInstance(data.getProject()).getModules();
    if (modules.length == 1) {
      setModule(modules[0]);
      myProjectLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      final List<Module> validModules = data.getValidModules();
      Module selection = validModules.size() > 0 ? validModules.get(0) : null;
      myModuleCombo.setModules(validModules);
      myModuleCombo.setSelectedModule(selection);
      myModuleCombo.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateDefaultInterpreter(myModuleCombo.getSelectedModule());
        }
      });
      updateDefaultInterpreter(myModuleCombo.getSelectedModule());
    }

    setAnchor(myEnvsComponent.getLabel());
    myPathMappingsComponent.setAnchor(myEnvsComponent.getLabel());

    if (data.showConfigureInterpretersLink()) {
      myConfigureInterpretersPanel.add(new PyConfigureInterpretersLinkPanel(myMainPanel));
    }

    addInterpreterComboBoxActionListener(new ActionListener() {
                                           @Override
                                           public void actionPerformed(ActionEvent event) {
                                             updateRemoteInterpreterMode();
                                           }
                                         }
    );

    updateRemoteInterpreterMode();

    final HideableDecorator decorator =
      new HideableDecorator(myHideablePanel, PyBundle.message("python.sdk.common.options.environment"), false) {
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
    decorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
    decorator.setContentComponent(myMainPanel);


    addInterpreterModeListener((b) ->
                                 myPathMappingsComponent.setVisible(b)
    );
  }

  @Override
  public JComponent getMainPanel() {
    return myHideablePanel;
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
    myEnvsComponent.setAnchor(anchor);
    myPythonInterpreterJBLabel.setAnchor(anchor);
  }

  @Override
  public String getInterpreterOptions() {
    return myInterpreterOptionsTextField.getText().trim();
  }

  @Override
  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptionsTextField.setText(interpreterOptions);
  }

  @Override
  public String getWorkingDirectory() {
    return FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getText().trim());
  }

  @Override
  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectoryTextField.setText(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
  }

  @Override
  public String getSdkHome() {
    final Sdk selectedSdk = (Sdk)myInterpreterComboBox.getSelectedItem();
    return selectedSdk == null ? null : selectedSdk.getHomePath();
  }

  @Override
  public void setSdkHome(String sdkHome) {
    mySelectedSdkHome = sdkHome;
  }

  @Nullable
  @Override
  public Module getModule() {
    final Module selectedItem = myModuleCombo.getSelectedModule();
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
    myModuleCombo.setSelectedModule(module);
    updateDefaultInterpreter(module);
  }

  private void updateDefaultInterpreter(Module module) {
    final Sdk sdk = module == null ? null : ModuleRootManager.getInstance(module).getSdk();
    myInterpreterComboBox.setRenderer(
      sdk == null
      ? new PySdkListCellRenderer(null)
      : new PySdkListCellRenderer(null, PyBundle.message("python.sdk.rendering.project.default.0", sdk.getName()), sdk)
    );
  }

  public void updateSdkList(boolean preserveSelection, PyConfigurableInterpreterList myInterpreterList) {
    myPythonSdks = myInterpreterList.getAllPythonSdks(myProject);
    Sdk selection = preserveSelection ? (Sdk)myInterpreterComboBox.getSelectedItem() : null;
    if (!myPythonSdks.contains(selection)) {
      selection = null;
    }
    myPythonSdks.add(0, null);
    myInterpreterComboBox.setModel(new CollectionComboBoxModel(myPythonSdks, selection));
  }

  @Override
  public boolean isUseModuleSdk() {
    return myInterpreterComboBox.getSelectedItem() == null;
  }

  @Override
  public void setUseModuleSdk(boolean useModuleSdk) {
    myInterpreterComboBox.setSelectedItem(useModuleSdk ? null : PythonSdkUtil.findSdkByPath(myPythonSdks, mySelectedSdkHome));
  }

  @Override
  public boolean isPassParentEnvs() {
    return myEnvsComponent.isPassParentEnvs();
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    myEnvsComponent.setPassParentEnvs(passParentEnvs);
  }

  @Override
  public Map<String, String> getEnvs() {
    return myEnvsComponent.getEnvs();
  }

  @Override
  public void setEnvs(Map<String, String> envs) {
    myEnvsComponent.setEnvs(envs);
  }

  @Override
  @Nullable
  public PathMappingSettings getMappingSettings() {
    if (myInterpreterRemote) {
      return myPathMappingsComponent.getMappingSettings();
    }
    else {
      return new PathMappingSettings();
    }
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    myPathMappingsComponent.setMappingSettings(mappingSettings);
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

  private void createUIComponents() {
    myInterpreterComboBox = new ComboBox(100);
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

  @Nullable
  private Sdk getSdkSelected() {
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
    myInterpreterComboBox.addActionListener(listener);
  }

  @Override
  public void removeInterpreterComboBoxActionListener(ActionListener listener) {
    myInterpreterComboBox.removeActionListener(listener);
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

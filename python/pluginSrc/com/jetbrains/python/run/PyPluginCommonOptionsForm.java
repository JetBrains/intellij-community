package com.jetbrains.python.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleTextAttributes;
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

  public PyPluginCommonOptionsForm(AbstractPythonRunConfiguration configuration) {
    // setting modules
    final List<Module> validModules = configuration.getValidModules();
    Module selection = validModules.size() > 0 ? validModules.get(0) : null;
    myModuleComboBox.setModel(new CollectionComboBoxModel(validModules, selection));
    myModuleComboBox.setRenderer(new HtmlListCellRenderer<Module>(myModuleComboBox.getRenderer()) {
      @Override
      protected void doCustomize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
        if (module == null) {
          append("[none]", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          setIcon(module.getModuleType().getNodeIcon(false));
          append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });

    myInterpreterComboBox.setRenderer(new SdkListCellRenderer("<Project Default>", myInterpreterComboBox.getRenderer()));
    myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", configuration.getProject(),
                                                  new FileChooserDescriptor(false, true, false, false, false, false));

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myUseSpecifiedSdkRadioButton.addActionListener(listener);
    myUseModuleSdkRadioButton.addActionListener(listener);
  }

  private void updateControls() {
    myModuleComboBox.setEnabled(myUseModuleSdkRadioButton.isSelected());
    myInterpreterComboBox.setEnabled(myUseSpecifiedSdkRadioButton.isSelected());
  }

  public JPanel getMainPanel() {
    return myMainPanel;
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
      if (FileUtil.pathsEqual(sdk.getHomePath(), sdkHome)) selection = sdk;
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
}

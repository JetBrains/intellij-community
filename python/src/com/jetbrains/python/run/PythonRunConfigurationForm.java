/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.PySymbolFieldWithBrowseButton;
import com.jetbrains.extensions.python.FileChooserDescriptorExtKt;
import com.jetbrains.extenstions.ContextAnchor;
import com.jetbrains.extenstions.ModuleBasedContextAnchor;
import com.jetbrains.extenstions.ProjectSdkContextAnchor;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams, PanelWithAnchor {
  public static final String SCRIPT_PATH = "Script path";
  public static final String MODULE_NAME = "Module name";
  private JPanel myRootPanel;
  private TextFieldWithBrowseButton myScriptTextField;
  private RawCommandLineEditor myScriptParametersTextField;
  private JPanel myCommonOptionsPlaceholder;
  private JBLabel myScriptParametersLabel;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;
  private final Project myProject;
  private JBCheckBox myShowCommandLineCheckbox;
  private JBCheckBox myEmulateTerminalCheckbox;
  private PySymbolFieldWithBrowseButton myModuleField;
  private JBComboBoxLabel myTargetComboBox;
  private JPanel myModuleFieldPanel;
  private boolean myModuleMode;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsForm.addInterpreterModeListener((isRemoteInterpreter) -> {
      emulateTerminalEnabled(!isRemoteInterpreter);
    });
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

    myProject = configuration.getProject();

    final FileChooserDescriptor chooserDescriptor =
      FileChooserDescriptorExtKt.withPythonFiles(FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle("Select Script"), true);

    final PyBrowseActionListener listener = new PyBrowseActionListener(configuration, chooserDescriptor) {

      @Override
      protected void onFileChosen(@NotNull final VirtualFile chosenFile) {
        super.onFileChosen(chosenFile);
        myCommonOptionsForm.setWorkingDirectory(chosenFile.getParent().getPath());
      }
    };

    myScriptTextField.addBrowseFolderListener(listener);

    if (SystemInfo.isWindows) {
      //TODO: enable it on Windows when it works there
      emulateTerminalEnabled(false);
    }


    //myTargetComboBox.setSelectedIndex(0);
    myEmulateTerminalCheckbox.setSelected(false);

    myEmulateTerminalCheckbox.addChangeListener(
      (ChangeEvent e) -> updateShowCommandLineEnabled());

    setAnchor(myCommonOptionsForm.getAnchor());

    final Module module = configuration.getModule();
    final Sdk sdk = configuration.getSdk();

    final ContextAnchor contentAnchor =
      (module != null ? new ModuleBasedContextAnchor(module) : new ProjectSdkContextAnchor(myProject, sdk));
    myModuleField = new PySymbolFieldWithBrowseButton(contentAnchor,
                                                      element -> element instanceof PyFile, () -> {
      final String workingDirectory = myCommonOptionsForm.getWorkingDirectory();
      if (StringUtil.isEmpty(workingDirectory)) {
        return null;
      }
      return LocalFileSystem.getInstance().findFileByPath(workingDirectory);
    });

    myModuleFieldPanel.add(myModuleField, BorderLayout.CENTER);

    //myTargetComboBox.addActionListener(e -> updateRunModuleMode());
  }

  private void updateRunModuleMode() {
    boolean mode = (MODULE_NAME + ":").equals(myTargetComboBox.getText());
    checkTargetComboConsistency(mode);
    setModuleModeInternal(mode);
  }

  private void checkTargetComboConsistency(boolean mode) {
    String item = myTargetComboBox.getText();
    assert item != null;
    //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
    if (mode && !item.toLowerCase().contains("module")) {
      throw new IllegalArgumentException("This option should refer to a module");
    }
  }

  private void updateShowCommandLineEnabled() {
    myShowCommandLineCheckbox.setEnabled(!myEmulateTerminalCheckbox.isVisible() || !myEmulateTerminalCheckbox.isSelected());
  }

  private void emulateTerminalEnabled(boolean flag) {
    myEmulateTerminalCheckbox.setVisible(flag);
    updateShowCommandLineEnabled();
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return myCommonOptionsForm;
  }

  @Override
  public String getScriptName() {
    if (isModuleMode()) {
      return myModuleField.getText().trim();
    }
    else {
      return FileUtil.toSystemIndependentName(myScriptTextField.getText().trim());
    }
  }

  @Override
  public void setScriptName(String scriptName) {
    if (isModuleMode()) {
      myModuleField.setText(StringUtil.notNullize(scriptName));
    }
    else {
      myScriptTextField.setText(scriptName == null ? "" : FileUtil.toSystemDependentName(scriptName));
    }
  }

  @Override
  public String getScriptParameters() {
    return myScriptParametersTextField.getText().trim();
  }

  @Override
  public void setScriptParameters(String scriptParameters) {
    myScriptParametersTextField.setText(scriptParameters);
  }

  @Override
  public boolean showCommandLineAfterwards() {
    return myShowCommandLineCheckbox.isSelected();
  }

  @Override
  public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards) {
    myShowCommandLineCheckbox.setSelected(showCommandLineAfterwards);
  }

  @Override
  public boolean emulateTerminal() {
    return myEmulateTerminalCheckbox.isSelected();
  }

  @Override
  public void setEmulateTerminal(boolean emulateTerminal) {
    myEmulateTerminalCheckbox.setSelected(emulateTerminal);
  }

  @Override
  public boolean isModuleMode() {
    return myModuleMode;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  public boolean isMultiprocessMode() {
    return PyDebuggerOptionsProvider.getInstance(myProject).isAttachToSubprocess();
  }

  public void setMultiprocessMode(boolean multiprocess) {
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }

  @Override
  public void setModuleMode(boolean moduleMode) {
    setTargetComboBoxValue(moduleMode ? MODULE_NAME : SCRIPT_PATH);
    updateRunModuleMode();
    checkTargetComboConsistency(moduleMode);
  }

  private void setModuleModeInternal(boolean moduleMode) {
    myModuleMode = moduleMode;

    myScriptTextField.setVisible(!moduleMode);
    myModuleFieldPanel.setVisible(moduleMode);
  }

  private void createUIComponents() {
    myTargetComboBox = new MyComboBox();
  }

  private void setTargetComboBoxValue(String text) {
    myTargetComboBox.setText(text + ":");
  }

  private class MyComboBox extends JBComboBoxLabel implements UserActivityProviderComponent {
    private final List<ChangeListener> myListeners = Lists.newArrayList();

    public MyComboBox() {
      this.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          JBPopupFactory.getInstance().createListPopup(
            new BaseListPopupStep<String>("Choose target to run", Lists.newArrayList(SCRIPT_PATH, MODULE_NAME)) {
              @Override
              public PopupStep onChosen(String selectedValue, boolean finalChoice) {
                setTargetComboBoxValue(selectedValue);
                updateRunModuleMode();
                return FINAL_CHOICE;
              }
            }).showUnderneathOf(MyComboBox.this);
        }
      });
    }

    @Override
    public void addChangeListener(ChangeListener changeListener) {
      myListeners.add(changeListener);
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
      myListeners.remove(changeListener);
    }

    void fireChangeEvent() {
      for (ChangeListener l : myListeners) {
        l.stateChanged(new ChangeEvent(this));
      }
    }

    @Override
    public void setText(String text) {
      super.setText(text);

      fireChangeEvent();
    }
  }
}

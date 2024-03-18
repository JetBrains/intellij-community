// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ui.CommonProgramParametersPanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.extensions.FileChooserDescriptorExtKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;


public class PythonRunConfigurationForm implements PythonRunConfigurationParams, PanelWithAnchor {
  private final PythonRunConfigurationPanel content;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private JComponent anchor;
  private final Project myProject;
  private boolean myModuleMode;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsForm.addInterpreterModeListener((isRemoteInterpreter) -> emulateTerminalEnabled(!isRemoteInterpreter));

    content = new PythonRunConfigurationPanel(configuration, myCommonOptionsForm, new MyComboBox());

    myProject = configuration.getProject();

    final FileChooserDescriptor chooserDescriptor =
      FileChooserDescriptorExtKt
        .withPythonFiles(FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(PyBundle.message("python.run.select.script")), true);

    final PyBrowseActionListener listener = new PyBrowseActionListener(configuration, chooserDescriptor) {

      @Override
      protected void onFileChosen(@NotNull final VirtualFile chosenFile) {
        super.onFileChosen(chosenFile);
        myCommonOptionsForm.setWorkingDirectory(chosenFile.getParent().getPath());
      }
    };

    content.scriptTextField.addBrowseFolderListener(listener);

    if (SystemInfo.isWindows) {
      //TODO: enable it on Windows when it works there
      emulateTerminalEnabled(false);
    }

    //myTargetComboBox.setSelectedIndex(0);
    content.emulateTerminalCheckbox.setSelected(false);

    //myTargetComboBox.addActionListener(e -> updateRunModuleMode());

    content.inputFileTextFieldWithBrowseButton.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor(), myProject));
    CommonProgramParametersPanel.addMacroSupport(content.scriptParametersTextField.getEditorField());
  }

  private void updateRunModuleMode() {
    boolean mode = (getModuleNameText() + ":").equals(content.targetComboBox.getText());
    setModuleModeInternal(mode);
  }

  private void emulateTerminalEnabled(boolean flag) {
    content.emulateTerminalCheckbox.setVisible(flag);
  }

  public JComponent getPanel() {
    return content.panel;
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return myCommonOptionsForm;
  }

  @Override
  public String getScriptName() {
    if (isModuleMode()) {
      return content.moduleField.getText().trim();
    }
    else {
      return FileUtil.toSystemIndependentName(content.scriptTextField.getText().trim());
    }
  }

  @Override
  public void setScriptName(String scriptName) {
    if (isModuleMode()) {
      content.moduleField.setText(StringUtil.notNullize(scriptName));
    }
    else {
      content.scriptTextField.setText(scriptName == null ? "" : FileUtil.toSystemDependentName(scriptName));
    }
  }

  @Override
  public String getScriptParameters() {
    return content.scriptParametersTextField.getText().trim();
  }

  @Override
  public void setScriptParameters(String scriptParameters) {
    content.scriptParametersTextField.setText(scriptParameters);
  }

  @Override
  public boolean showCommandLineAfterwards() {
    return content.showCommandLineCheckbox.isSelected();
  }

  @Override
  public void setShowCommandLineAfterwards(boolean showCommandLineAfterwards) {
    content.showCommandLineCheckbox.setSelected(showCommandLineAfterwards);
  }

  @Override
  public boolean emulateTerminal() {
    return content.emulateTerminalCheckbox.isSelected();
  }

  @Override
  public void setEmulateTerminal(boolean emulateTerminal) {
    content.emulateTerminalCheckbox.setSelected(emulateTerminal);
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

  @Nls public static String getScriptPathText() {
    return PyBundle.message("runcfg.labels.script.path");
  }

  @Nls public static String getModuleNameText() {
    return PyBundle.message("runcfg.labels.module.name");
  }

  @Nls public static String getCustomNameText() {
    return PyBundle.message("runcfg.labels.custom.name");
  }

  @Override
  @NotNull
  public String getInputFile() {
    return content.inputFileTextFieldWithBrowseButton.getText();
  }

  @Override
  public void setInputFile(@NotNull String inputFile) {
    content.inputFileTextFieldWithBrowseButton.setText(inputFile);
  }

  @Override
  public boolean isRedirectInput() {
    return content.redirectInputCheckBox.isSelected();
  }

  @Override
  public void setRedirectInput(boolean isRedirectInput) {
    content.redirectInputCheckBox.setSelected(isRedirectInput);
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myCommonOptionsForm.setAnchor(anchor);
  }

  @Override
  public void setModuleMode(boolean moduleMode) {
    setTargetComboBoxValue(moduleMode ? getModuleNameText() : getScriptPathText());
    setModuleModeInternal(moduleMode);
  }

  private void setModuleModeInternal(boolean moduleMode) {
    myModuleMode = moduleMode;
    content.setModuleMode(moduleMode);
  }

  private void setTargetComboBoxValue(@NlsContexts.Label String text) {
    content.targetComboBox.setText(text + ":");
  }

  private class MyComboBox extends JBComboBoxLabel implements UserActivityProviderComponent {
    private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    MyComboBox() {
      this.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          JBPopupFactory.getInstance().createListPopup(
            new BaseListPopupStep<@Nls String>(PyBundle.message("python.configuration.choose.target.to.run"), Lists.newArrayList(getScriptPathText(), getModuleNameText())) {
              @Override
              public PopupStep onChosen(@Nls String selectedValue, boolean finalChoice) {
                setTargetComboBoxValue(selectedValue);
                updateRunModuleMode();
                return FINAL_CHOICE;
              }
            }).showUnderneathOf(MyComboBox.this);
        }
      });
    }

    @Override
    public void addChangeListener(@NotNull ChangeListener changeListener) {
      myListeners.add(changeListener);
    }

    @Override
    public void removeChangeListener(@NotNull ChangeListener changeListener) {
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

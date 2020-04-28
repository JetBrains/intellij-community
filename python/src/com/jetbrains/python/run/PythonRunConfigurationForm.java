// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ui.CommonProgramParametersPanel;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.jetbrains.PySymbolFieldWithBrowseButton;
import com.jetbrains.PySymbolFieldWithBrowseButtonKt;
import com.jetbrains.extensions.ContextAnchor;
import com.jetbrains.extensions.ModuleBasedContextAnchor;
import com.jetbrains.extensions.ProjectSdkContextAnchor;
import com.jetbrains.extensions.python.FileChooserDescriptorExtKt;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;

/**
 * @author yole
 */
public class PythonRunConfigurationForm implements PythonRunConfigurationParams, PanelWithAnchor {
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
  private final PySymbolFieldWithBrowseButton myModuleField;
  private JBComboBoxLabel myTargetComboBox;
  private JPanel myModuleFieldPanel;
  private TextFieldWithBrowseButton myInputFileTextFieldWithBrowseButton;
  private JPanel myExecutionOptionsPlaceholder;
  private JPanel myExecutionOptionsPanel;
  private JBCheckBox myRedirectInputCheckBox;
  private boolean myModuleMode;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsForm.addInterpreterModeListener((isRemoteInterpreter) -> emulateTerminalEnabled(!isRemoteInterpreter));
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

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

    myScriptTextField.addBrowseFolderListener(listener);

    if (SystemInfo.isWindows) {
      //TODO: enable it on Windows when it works there
      emulateTerminalEnabled(false);
    }


    //myTargetComboBox.setSelectedIndex(0);
    myEmulateTerminalCheckbox.setSelected(false);

    setAnchor(myRedirectInputCheckBox.getAnchor());

    final Module module = configuration.getModule();
    final Sdk sdk = configuration.getSdk();

    final ContextAnchor contentAnchor =
      (module != null ? new ModuleBasedContextAnchor(module) : new ProjectSdkContextAnchor(myProject, sdk));
    myModuleField = new PySymbolFieldWithBrowseButton(contentAnchor,
                                                      element -> element instanceof PsiFileSystemItem
                                                                 && PySymbolFieldWithBrowseButtonKt.isPythonModule(element), () -> {
      final String workingDirectory = myCommonOptionsForm.getWorkingDirectory();
      if (StringUtil.isEmpty(workingDirectory)) {
        return null;
      }
      return LocalFileSystem.getInstance().findFileByPath(workingDirectory);
    });

    myModuleFieldPanel.add(myModuleField, BorderLayout.CENTER);

    //myTargetComboBox.addActionListener(e -> updateRunModuleMode());

    myInputFileTextFieldWithBrowseButton.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor(), myProject));
    HideableDecorator executionOptionsDecorator = new HideableDecorator(myExecutionOptionsPlaceholder, PyBundle.message("runcfg.labels.execution"), false);
    myExecutionOptionsPanel.setBorder(JBUI.Borders.empty(5, 0));
    executionOptionsDecorator.setOn(true);
    executionOptionsDecorator.setContentComponent(myExecutionOptionsPanel);

    myRedirectInputCheckBox.addItemListener(e -> myInputFileTextFieldWithBrowseButton.setEnabled(myRedirectInputCheckBox.isSelected()));

    final ButtonGroup group = new ButtonGroup() {
      @Override
      public void setSelected(ButtonModel model, boolean isSelected) {
        if (!isSelected && Objects.equals(getSelection(), model)) {
          clearSelection();
          return;
        }
        super.setSelected(model, isSelected);
      }
    };
    group.add(myEmulateTerminalCheckbox);
    group.add(myRedirectInputCheckBox);
    group.add(myShowCommandLineCheckbox);

    CommonProgramParametersPanel.addMacroSupport(myScriptParametersTextField.getEditorField());
  }

  private void updateRunModuleMode() {
    boolean mode = (getModuleNameText() + ":").equals(myTargetComboBox.getText());
    checkTargetComboConsistency(mode);
    setModuleModeInternal(mode);
  }

  private void checkTargetComboConsistency(boolean mode) {
    String item = myTargetComboBox.getText();
    assert item != null;
    if (mode && !StringUtil.toLowerCase(item).contains("module")) {
      throw new IllegalArgumentException("This option should refer to a module");
    }
  }

  private void emulateTerminalEnabled(boolean flag) {
    myEmulateTerminalCheckbox.setVisible(flag);
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

  @Nls public static String getScriptPathText() {
    return PyBundle.message("runcfg.labels.script.path");
  }

  @Nls public static String getModuleNameText() {
    return PyBundle.message("runcfg.labels.module.name");
  }

  @Override
  @NotNull
  public String getInputFile() {
    return myInputFileTextFieldWithBrowseButton.getText();
  }

  @Override
  public void setInputFile(@NotNull String inputFile) {
    myInputFileTextFieldWithBrowseButton.setText(inputFile);
  }

  @Override
  public boolean isRedirectInput() {
    return myRedirectInputCheckBox.isSelected();
  }

  @Override
  public void setRedirectInput(boolean isRedirectInput) {
    myRedirectInputCheckBox.setSelected(isRedirectInput);
    myInputFileTextFieldWithBrowseButton.setEnabled(isRedirectInput);
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
    myRedirectInputCheckBox.setAnchor(anchor);
  }

  @Override
  public void setModuleMode(boolean moduleMode) {
    setTargetComboBoxValue(moduleMode ? getModuleNameText() : getScriptPathText());
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
    private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    MyComboBox() {
      this.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          JBPopupFactory.getInstance().createListPopup(
            new BaseListPopupStep<String>(PyBundle.message("python.configuration.choose.target.to.run"), Lists.newArrayList(getScriptPathText(), getModuleNameText())) {
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

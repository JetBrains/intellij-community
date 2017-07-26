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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

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
  private JBCheckBox myMixedDebugCheckbox;
  private RawCommandLineEditor myDebuggableNativeLibsTextField;

  public PythonRunConfigurationForm(PythonRunConfiguration configuration) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsForm.addInterpreterModeListener((isRemoteInterpreter) -> {
      emulateTerminalEnabled(!isRemoteInterpreter);
    });
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel(), BorderLayout.CENTER);

    myProject = configuration.getProject();

    FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return file.isDirectory() || file.getExtension() == null || Comparing.equal(file.getExtension(), "py");
      }
    };
    //chooserDescriptor.setRoot(s.getProject().getBaseDir());

    ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Script", "", myScriptTextField, myProject,
                                                                           chooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT) {

        @Override
        protected void onFileChosen(@NotNull VirtualFile chosenFile) {
          super.onFileChosen(chosenFile);
          myCommonOptionsForm.setWorkingDirectory(chosenFile.getParent().getPath());
        }
      };

    myScriptTextField.addActionListener(listener);

    if (SystemInfo.isWindows) {
      //TODO: enable it on Windows when it works there
      emulateTerminalEnabled(false);
    }

    myEmulateTerminalCheckbox.setSelected(false);

    myEmulateTerminalCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateShowCommandLineEnabled();
      }
    });

    setAnchor(myCommonOptionsForm.getAnchor());
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
    return FileUtil.toSystemIndependentName(myScriptTextField.getText().trim());
  }

  @Override
  public void setScriptName(String scriptName) {
    myScriptTextField.setText(scriptName == null ? "" : FileUtil.toSystemDependentName(scriptName));
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
  public boolean mixedDebugMode() {
    return myMixedDebugCheckbox.isSelected();
  }

  @Override
  public void setMixedDebugMode(boolean mixedDebugMode) {
    myMixedDebugCheckbox.setSelected(mixedDebugMode);
  }

  @Override
  public String getDebuggableExternalLibs() {
    return myDebuggableNativeLibsTextField.getText();
  }

  @Override
  public void setDebuggableExternalLibs(String debuggableExternalLibs) {
    myDebuggableNativeLibsTextField.setText(debuggableExternalLibs);
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
}

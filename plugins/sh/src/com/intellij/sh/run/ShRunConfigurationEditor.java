// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.sh.ShBundle;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShRunConfigurationEditor extends SettingsEditor<ShRunConfiguration> {
  private JPanel myPanel;
  private JPanel myScriptPathPanel;
  private JPanel myScriptTextPanel;
  private RawCommandLineEditor myScript;
  private TextFieldWithBrowseButton myScriptSelector;
  private RawCommandLineEditor myScriptOptions;
  private TextFieldWithBrowseButton myScriptFileWorkingDirectory;
  private TextFieldWithBrowseButton myScriptWorkingDirectory;
  private TextFieldWithBrowseButton myInterpreterSelector;
  private RawCommandLineEditor myInterpreterOptions;
  private JBCheckBox myExecuteFileInTerminal;
  private JBCheckBox myExecuteScriptInTerminal;
  private EnvironmentVariablesComponent myEnvComponent;
  private ButtonGroup myScriptGroup;
  private JBRadioButton myScriptFileRadioButton;
  private JBRadioButton myScriptTextRadioButton;

  ShRunConfigurationEditor(Project project) {
    myScriptSelector.addBrowseFolderListener(ShBundle.message("sh.label.choose.shell.script"), "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myScriptFileWorkingDirectory.addBrowseFolderListener(ShBundle.message("sh.label.choose.script.working.directory"), "", project,
                                                         FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myScriptWorkingDirectory.addBrowseFolderListener(ShBundle.message("sh.label.choose.script.working.directory"), "", project,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myInterpreterSelector.addBrowseFolderListener(ShBundle.message("sh.label.choose.interpreter"), "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());

    myScriptGroup = new ButtonGroup();
    myScriptGroup.add(myScriptTextRadioButton);
    myScriptGroup.add(myScriptFileRadioButton);
    myScriptFileRadioButton.addActionListener(action -> selectMode());
    myScriptTextRadioButton.addActionListener(action -> selectMode());
    selectMode();
  }

  @Override
  protected void resetEditorFrom(@NotNull ShRunConfiguration configuration) {
    if (configuration.isExecuteScriptFile()) {
      myScriptFileRadioButton.setSelected(true);
    } else {
      myScriptTextRadioButton.setSelected(true);
    }
    selectMode();
    // Configure UI for script text execution
    myScript.setText(configuration.getScriptText());
    myScriptWorkingDirectory.setText(configuration.getScriptWorkingDirectory());
    myExecuteScriptInTerminal.setSelected(configuration.isExecuteInTerminal());

    // Configure script by path execution
    myScriptSelector.setText(configuration.getScriptPath());
    myScriptOptions.setText(configuration.getScriptOptions());
    myScriptFileWorkingDirectory.setText(configuration.getScriptWorkingDirectory());
    myInterpreterSelector.setText(configuration.getInterpreterPath());
    myInterpreterOptions.setText(configuration.getInterpreterOptions());
    myExecuteFileInTerminal.setSelected(configuration.isExecuteInTerminal());
    myEnvComponent.setEnvData(configuration.getEnvData());
  }

  @Override
  protected void applyEditorTo(@NotNull ShRunConfiguration configuration) {
    configuration.setScriptText(myScript.getText());
    // If we execute script by path, fill one components or other components if execute script text
    if (myScriptFileRadioButton.isSelected()) {
      configuration.setScriptWorkingDirectory(myScriptFileWorkingDirectory.getText());
      configuration.setExecuteInTerminal(myExecuteFileInTerminal.isSelected());
      configuration.setExecuteScriptFile(true);
    } else {
      configuration.setScriptWorkingDirectory(myScriptWorkingDirectory.getText());
      configuration.setExecuteInTerminal(myExecuteScriptInTerminal.isSelected());
      configuration.setExecuteScriptFile(false);
    }
    configuration.setScriptPath(myScriptSelector.getText());
    configuration.setScriptOptions(myScriptOptions.getText());
    configuration.setInterpreterPath(myInterpreterSelector.getText());
    configuration.setInterpreterOptions(myInterpreterOptions.getText());
    configuration.setEnvData(myEnvComponent.getEnvData());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel;
  }

  private void selectMode() {
    boolean scriptExecutionSelected = myScriptFileRadioButton.isSelected();
    myScriptFileRadioButton.setSelected(scriptExecutionSelected);
    myScriptPathPanel.setVisible(scriptExecutionSelected);
    myScriptTextRadioButton.setSelected(!scriptExecutionSelected);
    myScriptTextPanel.setVisible(!scriptExecutionSelected);
  }
}

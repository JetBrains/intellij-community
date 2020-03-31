// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.sh.ShBundle;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShRunConfigurationEditor extends SettingsEditor<ShRunConfiguration> {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myScriptSelector;
  private RawCommandLineEditor myScriptOptions;
  private TextFieldWithBrowseButton myScriptWorkingDirectory;
  private TextFieldWithBrowseButton myInterpreterSelector;
  private RawCommandLineEditor myInterpreterOptions;

  ShRunConfigurationEditor(Project project) {
    myScriptSelector.addBrowseFolderListener(ShBundle.message("sh.label.choose.shell.script"), "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myScriptWorkingDirectory.addBrowseFolderListener(ShBundle.message("sh.label.choose.script.working.directory"), "", project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myInterpreterSelector.addBrowseFolderListener(ShBundle.message("sh.label.choose.interpreter"), "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
  }

  @Override
  protected void resetEditorFrom(@NotNull ShRunConfiguration configuration) {
    myScriptSelector.setText(configuration.getScriptPath());
    myScriptOptions.setText(configuration.getScriptOptions());
    myScriptWorkingDirectory.setText(configuration.getScriptWorkingDirectory());
    myInterpreterSelector.setText(configuration.getInterpreterPath());
    myInterpreterOptions.setText(configuration.getInterpreterOptions());
  }

  @Override
  protected void applyEditorTo(@NotNull ShRunConfiguration configuration) throws ConfigurationException {
    configuration.setScriptPath(myScriptSelector.getText());
    configuration.setScriptOptions(myScriptOptions.getText());
    configuration.setScriptWorkingDirectory(myScriptWorkingDirectory.getText());
    configuration.setInterpreterPath(myInterpreterSelector.getText());
    configuration.setInterpreterOptions(myInterpreterOptions.getText());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel;
  }
}

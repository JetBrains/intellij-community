// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pytestLegacy;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class PyTestConfigurationEditor extends SettingsEditor<PyTestRunConfiguration> implements PanelWithAnchor,
                                                                                                 PyTestRunConfigurationParams {
  private JPanel myMainPanel;
  private JPanel myCommonOptionsPlaceholder;
  private JTextField myKeywordsTextField;
  private TextFieldWithBrowseButton myTestScriptTextField;
  private JTextField myParamsTextField;
  private JBLabel myTargetLabel;
  private JCheckBox myParametersCheckBox;
  private JCheckBox myKeywordsCheckBox;
  private JPanel myRootPanel;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private final Project myProject;
  private JComponent anchor;

  public PyTestConfigurationEditor(final Project project, PyTestRunConfiguration configuration) {
    myProject = project;
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel());

    String title = PyBundle.message("runcfg.unittest.dlg.select.script.path");
    final FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory
      .createSingleFileOrFolderDescriptor();
    fileChooserDescriptor.setTitle(title);
    myTestScriptTextField.addBrowseFolderListener(title, null, myProject, fileChooserDescriptor);

    myTargetLabel.setLabelFor(myTestScriptTextField);

    myParametersCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myParamsTextField.setEnabled(myParametersCheckBox.isSelected());
      }
    });

    myKeywordsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myKeywordsTextField.setEnabled(myKeywordsCheckBox.isSelected());
      }
    });

    myParametersCheckBox.setSelected(configuration.useParam());
    myKeywordsCheckBox.setSelected(configuration.useKeyword());

    myParamsTextField.setEnabled(configuration.useParam());
    myKeywordsTextField.setEnabled(configuration.useKeyword());

    setAnchor(myCommonOptionsForm.getAnchor());
  }

  @Override
  protected void resetEditorFrom(@NotNull PyTestRunConfiguration s) {
    AbstractPythonRunConfiguration.copyParams(s, myCommonOptionsForm);
    myKeywordsTextField.setText(s.getKeywords());
    myTestScriptTextField.setText(s.getTestToRun());
    myKeywordsCheckBox.setSelected(s.useKeyword());
    myParametersCheckBox.setSelected(s.useParam());
    myParamsTextField.setText(s.getParams());
  }

  @Override
  protected void applyEditorTo(@NotNull PyTestRunConfiguration s) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, s);
    s.setTestToRun(myTestScriptTextField.getText().trim());
    s.setKeywords(myKeywordsTextField.getText().trim());
    s.setParams(myParamsTextField.getText().trim());
    s.useKeyword(myKeywordsCheckBox.isSelected());
    s.useParam(myParametersCheckBox.isSelected());
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myRootPanel;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myTargetLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }

  @Override
  public boolean useParam() {
    return myParametersCheckBox.isSelected();
  }

  @Override
  public void useParam(boolean useParam) {
    myParametersCheckBox.setSelected(useParam);
  }

  @Override
  public boolean useKeyword() {
    return myKeywordsCheckBox.isSelected();
  }

  @Override
  public void useKeyword(boolean useKeyword) {
    myKeywordsCheckBox.setSelected(useKeyword);
  }
}

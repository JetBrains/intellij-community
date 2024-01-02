// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.run.AbstractPyCommonOptionsForm;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PyCommonOptionsFormFactory;
import com.intellij.python.reStructuredText.RestBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Users : catherine
 */
public class RestConfigurationEditor extends SettingsEditor<RestRunConfiguration> implements PanelWithAnchor {
  private JPanel myMainPanel;
  private JPanel myCommonOptionsPlaceholder;
  private TextFieldWithBrowseButton myInputFileField;
  private JTextField myParamsTextField;
  private JCheckBox myOpenInBrowser;
  private TextFieldWithBrowseButton myOutputFileField;
  private JComboBox myTasks;
  private JBLabel myCommandLabel;
  private JLabel myConfigurationName;
  private final AbstractPyCommonOptionsForm myCommonOptionsForm;
  private final Project myProject;
  private JComponent anchor;

  public RestConfigurationEditor(final Project project,
                                 AbstractPythonRunConfiguration configuration,
                                 CollectionComboBoxModel model) {
    myCommonOptionsForm = PyCommonOptionsFormFactory.getInstance().createForm(configuration.getCommonOptionsFormData());
    myCommonOptionsPlaceholder.add(myCommonOptionsForm.getMainPanel());
    myProject = project;
    myTasks.setModel(model);
    myTasks.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        Object task = myTasks.getSelectedItem();
        if (task != null &&
            (task.toString().equals("rst2latex") ||
             task.toString().equals("rst2odt")))
          myOpenInBrowser.setEnabled(false);
        else
          myOpenInBrowser.setEnabled(true);
      }
    });
    myOpenInBrowser.setSelected(false);

    setAnchor(myCommonOptionsForm.getAnchor());
  }

  @Override
  protected void resetEditorFrom(@NotNull RestRunConfiguration configuration) {
    AbstractPythonRunConfiguration.copyParams(configuration,
                                              myCommonOptionsForm);
    myInputFileField.setText(configuration.getInputFile());
    myOutputFileField.setText(configuration.getOutputFile());
    myParamsTextField.setText(configuration.getParams());
    myTasks.setSelectedItem(configuration.getTask());
    myOpenInBrowser.setSelected(configuration.openInBrowser());
    if (configuration.getTask().equals("rst2latex")
        || configuration.getTask().equals("rst2odt"))
      myOpenInBrowser.setEnabled(false);
    else
      myOpenInBrowser.setEnabled(true);
  }

  @Override
  protected void applyEditorTo(@NotNull RestRunConfiguration configuration) throws ConfigurationException {
    AbstractPythonRunConfiguration.copyParams(myCommonOptionsForm, configuration);
    configuration.setInputFile(myInputFileField.getText().trim());
    configuration.setOutputFile(myOutputFileField.getText().trim());
    configuration.setParams(myParamsTextField.getText().trim());
    Object task = myTasks.getSelectedItem();
    if (task != null)
      configuration.setTask(task.toString());


    configuration.setOpenInBrowser(myOpenInBrowser.isSelected());
    if (!myOpenInBrowser.isEnabled())
      configuration.setOpenInBrowser(false);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }

  public void setOpenInBrowserVisible(boolean visible) {
    myOpenInBrowser.setVisible(visible);
  }

  public void setInputDescriptor(FileChooserDescriptor descriptor) {
    String title = RestBundle.message("runcfg.dlg.select.script.path");
    myInputFileField.addBrowseFolderListener(title, null, myProject, descriptor);
  }

  public void setOutputDescriptor(FileChooserDescriptor descriptor) {
    String title = RestBundle.message("runcfg.dlg.select.script.path");
    myOutputFileField.addBrowseFolderListener(title, null, myProject, descriptor);
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    this.anchor = anchor;
    myCommandLabel.setAnchor(anchor);
    myCommonOptionsForm.setAnchor(anchor);
  }

  public void setConfigurationName(@NlsSafe String name) {
    myConfigurationName.setText(name);
  }
}

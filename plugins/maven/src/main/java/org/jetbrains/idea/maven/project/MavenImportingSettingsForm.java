/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.updateSettings.impl.LabelTextReplacingUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MavenImportingSettingsForm {
  private JPanel myPanel;

  private JCheckBox mySearchRecursivelyCheckBox;

  private JLabel myProjectFormatLabel;
  private JComboBox myProjectFormatComboBox;
  private ProjectFormatPanel myProjectFormatPanel;
  private JCheckBox mySeparateModulesDirCheckBox;
  private TextFieldWithBrowseButton mySeparateModulesDirChooser;

  private JCheckBox myImportAutomaticallyBox;
  private JCheckBox myCreateModulesForAggregators;
  private JCheckBox myCreateGroupsCheckBox;
  private JComboBox myUpdateFoldersOnImportPhaseComboBox;
  private JCheckBox myKeepSourceFoldersCheckBox;
  private JCheckBox myUseMavenOutputCheckBox;
  private JCheckBox myDownloadSourcesCheckBox;
  private JCheckBox myDownloadDocsCheckBox;

  private JPanel myAdditionalSettingsPanel;
  private JComboBox myGeneratedSourcesComboBox;
  private JCheckBox myExcludeTargetFolderCheckBox;
  private JTextField myDependencyTypes;

  public MavenImportingSettingsForm(boolean isImportStep, boolean isCreatingNewProject) {
    mySearchRecursivelyCheckBox.setVisible(isImportStep);
    myProjectFormatLabel.setVisible(isImportStep && isCreatingNewProject);
    myProjectFormatComboBox.setVisible(isImportStep && isCreatingNewProject);

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    mySeparateModulesDirCheckBox.addActionListener(listener);

    mySeparateModulesDirChooser.addBrowseFolderListener(ProjectBundle.message("maven.import.title.module.dir"), "", null,
                                                        FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myUpdateFoldersOnImportPhaseComboBox.setModel(new DefaultComboBoxModel(MavenImportingSettings.UPDATE_FOLDERS_PHASES));

    myGeneratedSourcesComboBox.setModel(new EnumComboBoxModel<>(MavenImportingSettings.GeneratedSourcesFolder.class));
    myGeneratedSourcesComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof MavenImportingSettings.GeneratedSourcesFolder) {
          setText(((MavenImportingSettings.GeneratedSourcesFolder)value).title);
        }
      }
    });

    LabelTextReplacingUtil.replaceText(myPanel);
  }

  private void createUIComponents() {
    myProjectFormatPanel = new ProjectFormatPanel();
    myProjectFormatComboBox = myProjectFormatPanel.getStorageFormatComboBox();
  }

  private void updateControls() {
    boolean useSeparateDir = mySeparateModulesDirCheckBox.isSelected();
    mySeparateModulesDirChooser.setEnabled(useSeparateDir);
    if (useSeparateDir && StringUtil.isEmptyOrSpaces(mySeparateModulesDirChooser.getText())) {
      mySeparateModulesDirChooser.setText(FileUtil.toSystemDependentName(getDefaultModuleDir()));
    }
  }

  public String getDefaultModuleDir() {
    return "";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void getData(MavenImportingSettings data) {
    data.setLookForNested(mySearchRecursivelyCheckBox.isSelected());
    data.setDedicatedModuleDir(mySeparateModulesDirCheckBox.isSelected() ? mySeparateModulesDirChooser.getText() : "");

    data.setImportAutomatically(myImportAutomaticallyBox.isSelected());
    data.setCreateModulesForAggregators(myCreateModulesForAggregators.isSelected());
    data.setCreateModuleGroups(myCreateGroupsCheckBox.isSelected());

    data.setKeepSourceFolders(myKeepSourceFoldersCheckBox.isSelected());
    data.setExcludeTargetFolder(myExcludeTargetFolderCheckBox.isSelected());
    data.setUseMavenOutput(myUseMavenOutputCheckBox.isSelected());

    data.setUpdateFoldersOnImportPhase((String)myUpdateFoldersOnImportPhaseComboBox.getSelectedItem());
    data.setGeneratedSourcesFolder((MavenImportingSettings.GeneratedSourcesFolder)myGeneratedSourcesComboBox.getSelectedItem());

    data.setDownloadSourcesAutomatically(myDownloadSourcesCheckBox.isSelected());
    data.setDownloadDocsAutomatically(myDownloadDocsCheckBox.isSelected());

    data.setDependencyTypes(myDependencyTypes.getText());
  }

  public void setData(MavenImportingSettings data) {
    mySearchRecursivelyCheckBox.setSelected(data.isLookForNested());

    mySeparateModulesDirCheckBox.setSelected(!StringUtil.isEmptyOrSpaces(data.getDedicatedModuleDir()));
    mySeparateModulesDirChooser.setText(data.getDedicatedModuleDir());

    myImportAutomaticallyBox.setSelected(data.isImportAutomatically());
    myCreateModulesForAggregators.setSelected(data.isCreateModulesForAggregators());
    myCreateGroupsCheckBox.setSelected(data.isCreateModuleGroups());

    myKeepSourceFoldersCheckBox.setSelected(data.isKeepSourceFolders());
    myExcludeTargetFolderCheckBox.setSelected(data.isExcludeTargetFolder());
    myUseMavenOutputCheckBox.setSelected(data.isUseMavenOutput());

    myUpdateFoldersOnImportPhaseComboBox.setSelectedItem(data.getUpdateFoldersOnImportPhase());
    myGeneratedSourcesComboBox.setSelectedItem(data.getGeneratedSourcesFolder());

    myDownloadSourcesCheckBox.setSelected(data.isDownloadSourcesAutomatically());
    myDownloadDocsCheckBox.setSelected(data.isDownloadDocsAutomatically());

    myDependencyTypes.setText(data.getDependencyTypes());

    updateControls();
  }

  public boolean isModified(MavenImportingSettings settings) {
    MavenImportingSettings formData = new MavenImportingSettings();
    getData(formData);
    return !formData.equals(settings);
  }

  public void updateData(WizardContext wizardContext) {
    myProjectFormatPanel.updateData(wizardContext);
  }

  public JPanel getAdditionalSettingsPanel() {
    return myAdditionalSettingsPanel;
  }
}

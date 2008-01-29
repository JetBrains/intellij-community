package org.jetbrains.idea.maven.project;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Vladislav.Kaznacheev
 */
public class ImporterSettingsForm {

  private JPanel panel;
  private JCheckBox myModuleDirCheckBox;
  private TextFieldWithBrowseButton myModuleDirControl;
  private JCheckBox myLookForNestedCheckBox;
  private JCheckBox myAutoSyncCheckBox;
  private JCheckBox myCreateGroupsCheckBox;
  private JCheckBox myUseMavenOutputCheckBox;
  private JTextArea myIgnoreDependenciesTextArea;
  private JPanel myIgnorePanel;

  public ImporterSettingsForm() {
    this(false);
  }

  public ImporterSettingsForm(boolean minimal) {

    ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        enableControls();
      }
    };
    myModuleDirCheckBox.addActionListener(listener);

    myModuleDirControl.addBrowseFolderListener(ProjectBundle.message("maven.import.title.module.dir"), "", null,
                                               new FileChooserDescriptor(false, true, false, false, false, false));

    if(minimal){
      myCreateGroupsCheckBox.setVisible(false);
      myUseMavenOutputCheckBox.setVisible(false);
      myIgnorePanel.setVisible(false);
      myIgnoreDependenciesTextArea.setVisible(false);
    }
  }

  private void enableControls() {
    final boolean dedicated = myModuleDirCheckBox.isSelected();
    myModuleDirControl.setEnabled(dedicated);
    if (dedicated && StringUtil.isEmptyOrSpaces(myModuleDirControl.getText())) {
      myModuleDirControl.setText(FileUtil.toSystemDependentName(getDefaultModuleDir()));
    }
  }

  public String getDefaultModuleDir() {
    return "";
  }

  public JComponent createComponent() {
    return panel;
  }

  public void getData(MavenImporterSettings data) {
    data.setDedicatedModuleDir(myModuleDirCheckBox.isSelected() ? myModuleDirControl.getText() : "");
    data.setCreateModuleGroups(myCreateGroupsCheckBox.isSelected());
    data.setAutoSync(myAutoSyncCheckBox.isSelected());
    data.setLookForNested(myLookForNestedCheckBox.isSelected());
    data.setUseMavenOutput(myUseMavenOutputCheckBox.isSelected());
    data.setIgnoredDependencies(Strings.tokenize(myIgnoreDependenciesTextArea.getText(), Strings.WHITESPACE + ",;"));
  }

  public void setData(final MavenImporterSettings data) {
    myModuleDirCheckBox.setSelected(!StringUtil.isEmptyOrSpaces(data.getDedicatedModuleDir()));
    myModuleDirControl.setText(data.getDedicatedModuleDir());

    myAutoSyncCheckBox.setSelected(data.isAutoSync());
    myCreateGroupsCheckBox.setSelected(data.isCreateModuleGroups());
    myLookForNestedCheckBox.setSelected(data.isLookForNested());
    myUseMavenOutputCheckBox.setSelected(data.isUseMavenOutput());
    myIgnoreDependenciesTextArea.setText(Strings.detokenize(data.getIgnoredDependencies(), ','));

    enableControls();
  }

  public boolean isModified(MavenImporterSettings settings) {
    MavenImporterSettings formData = new MavenImporterSettings();
    getData(formData);
    return !formData.equals(settings);
  }
}

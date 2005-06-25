package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowOptionsSettingImpl;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class VcsGeneralConfigurationPanel {

  private JCheckBox myForceNonEmptyComment;
  private JCheckBox myReuseLastComment;
  private JCheckBox myPutFocusIntoComment;
  private JCheckBox myShowReadOnlyStatusDialog;

  private JRadioButton myShowDialogOnAddingFile;
  private JRadioButton myPerformActionOnAddingFile;
  private JRadioButton myDoNothingOnAddingFile;

  private JRadioButton myShowDialogOnRemovingFile;
  private JRadioButton myPerformActionOnRemovingFile;
  private JRadioButton myDoNothingOnRemovingFile;

  private JPanel myPanel;

  private final JRadioButton[] myOnFileAddingGroup;
  private final JRadioButton[] myOnFileRemovingGroup;

  private final Project myProject;
  private JPanel myPromptsPanel;


  Map<VcsShowOptionsSettingImpl, JCheckBox> myPromptOptions = new LinkedHashMap<VcsShowOptionsSettingImpl, JCheckBox>();

  public VcsGeneralConfigurationPanel(final Project project) {

    myProject = project;

    myOnFileAddingGroup = new JRadioButton[]{
      myShowDialogOnAddingFile,
      myPerformActionOnAddingFile,
      myDoNothingOnAddingFile
    };

    myOnFileRemovingGroup = new JRadioButton[]{
      myShowDialogOnRemovingFile,
      myPerformActionOnRemovingFile,
      myDoNothingOnRemovingFile
    };

    myPromptsPanel.setLayout(new GridLayout(3, 0));

    List<VcsShowOptionsSettingImpl> options = ProjectLevelVcsManagerEx.getInstanceEx(project).getAllOptions();

    for (VcsShowOptionsSettingImpl setting : options) {
      if (!setting.getApplicableVcses().isEmpty()) {
        final JCheckBox checkBox = new JCheckBox(setting.getDisplayName());
        myPromptsPanel.add(checkBox);
        myPromptOptions.put(setting, checkBox);
      }
    }

    myPromptsPanel.setSize(myPromptsPanel.getPreferredSize());


  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.PUT_FOCUS_INTO_COMMENT = myPutFocusIntoComment.isSelected();
    settings.SAVE_LAST_COMMIT_MESSAGE = myReuseLastComment.isSelected();
    settings.FORCE_NON_EMPTY_COMMENT = myForceNonEmptyComment.isSelected();

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }

    settings.ON_FILE_ADDING = getSelected(myOnFileAddingGroup);
    settings.ON_FILE_REMOVING = getSelected(myOnFileRemovingGroup);


    getReadOnlyStatusHandler().SHOW_DIALOG = myShowReadOnlyStatusDialog.isSelected();
  }


  private int getSelected(JRadioButton[] group) {
    for (int i = 0; i < group.length; i++) {
      JRadioButton jRadioButton = group[i];
      if (jRadioButton.isSelected()) return i;
    }
    return -1;
  }


  private ReadonlyStatusHandlerImpl getReadOnlyStatusHandler() {
    return ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject));
  }

  public boolean isModified() {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    if (settings.PUT_FOCUS_INTO_COMMENT != myPutFocusIntoComment.isSelected()){
      return true;
    }
    if (settings.SAVE_LAST_COMMIT_MESSAGE != myReuseLastComment.isSelected()){
      return true;
    }
    if (settings.FORCE_NON_EMPTY_COMMENT != myForceNonEmptyComment.isSelected()){
      return true;
    }

    if (getReadOnlyStatusHandler().SHOW_DIALOG != myShowReadOnlyStatusDialog.isSelected()) {
      return true;
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      if (setting.getValue() != myPromptOptions.get(setting).isSelected()) return true;
    }

    return false;
  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myPutFocusIntoComment.setSelected(settings.PUT_FOCUS_INTO_COMMENT);
    myReuseLastComment.setSelected(settings.SAVE_LAST_COMMIT_MESSAGE);
    myForceNonEmptyComment.setSelected(settings.FORCE_NON_EMPTY_COMMENT);
    myShowReadOnlyStatusDialog.setSelected(getReadOnlyStatusHandler().SHOW_DIALOG);

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }

    myOnFileAddingGroup[settings.ON_FILE_ADDING].setSelected(true);
    myOnFileRemovingGroup[settings.ON_FILE_REMOVING].setSelected(true);
  }


  public JComponent getPanel() {
    return myPanel;
  }

  public void updateAvailableOptions(final Collection<AbstractVcs> activeVcses) {
    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      final JCheckBox checkBox = myPromptOptions.get(setting);
      checkBox.setEnabled(setting.isApplicableTo(activeVcses));
      checkBox.setToolTipText("Applicable to: " + composeText(setting.getApplicableVcses()));
    }
  }

  private String composeText(final List<AbstractVcs> applicableVcses) {
    final StringBuffer result = new StringBuffer();
    for (AbstractVcs abstractVcs : applicableVcses) {
      if (result.length() > 0) result.append(", ");
      result.append(abstractVcs.getDisplayName());
    }
    return result.toString();
  }
}

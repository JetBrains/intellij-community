package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.committed.CacheSettingsDialog;
import com.intellij.openapi.vcs.changes.ui.IgnoredSettingsDialog;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class VcsGeneralConfigurationPanel {

  private JCheckBox myForceNonEmptyComment;
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
  private JPanel myRemoveConfirmationPanel;
  private JPanel myAddConfirmationPanel;
  private JCheckBox myCbOfferToMoveChanges;
  private JCheckBox myCbUpdateInBackground;
  private JCheckBox myCbCommitInBackground;
  private JButton myConfigureIgnoredFilesButton;
  private JCheckBox myCbEditInBackground;
  private JCheckBox myCbAddRemoveInBackground;
  private JButton myConfigureHistoryCacheButton;
  private JComboBox myFailedCommitChangelistCombo;

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
      if (!setting.getApplicableVcses().isEmpty() || project.isDefault()) {
        final JCheckBox checkBox = new JCheckBox(setting.getDisplayName());
        myPromptsPanel.add(checkBox);
        myPromptOptions.put(setting, checkBox);
      }
    }

    myPromptsPanel.setSize(myPromptsPanel.getPreferredSize());

    myConfigureIgnoredFilesButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IgnoredSettingsDialog.configure(myProject);
      }
    });
    myConfigureHistoryCacheButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        CacheSettingsDialog.showSettingsDialog(myProject);
      }
    });
  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.FORCE_NON_EMPTY_COMMENT = myForceNonEmptyComment.isSelected();
    settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = myCbOfferToMoveChanges.isSelected();
    settings.PERFORM_COMMIT_IN_BACKGROUND = myCbCommitInBackground.isSelected();
    settings.PERFORM_UPDATE_IN_BACKGROUND = myCbUpdateInBackground.isSelected();
    settings.PERFORM_EDIT_IN_BACKGROUND = myCbEditInBackground.isSelected();
    settings.PERFORM_ADD_REMOVE_IN_BACKGROUND = myCbAddRemoveInBackground.isSelected();
    settings.MOVE_TO_FAILED_COMMIT_CHANGELIST = getFailedCommitConfirm();

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      setting.setValue(myPromptOptions.get(setting).isSelected());
    }

    getAddConfirmation().setValue(getSelected(myOnFileAddingGroup));
    getRemoveConfirmation().setValue(getSelected(myOnFileRemovingGroup));


    getReadOnlyStatusHandler().getState().SHOW_DIALOG = myShowReadOnlyStatusDialog.isSelected();
  }

  private VcsShowConfirmationOption.Value getFailedCommitConfirm() {
    switch(myFailedCommitChangelistCombo.getSelectedIndex()) {
      case 0: return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
      case 1: return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
      default: return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }
  }

  private VcsShowConfirmationOption getAddConfirmation() {
    return ProjectLevelVcsManagerEx.getInstanceEx(myProject)
      .getConfirmation(VcsConfiguration.StandardConfirmation.ADD);
  }

  private VcsShowConfirmationOption getRemoveConfirmation() {
    return ProjectLevelVcsManagerEx.getInstanceEx(myProject)
      .getConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
  }


  private static VcsShowConfirmationOption.Value getSelected(JRadioButton[] group) {
    if (group[0].isSelected()) return VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    if (group[1].isSelected()) return VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
    return VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
  }


  private ReadonlyStatusHandlerImpl getReadOnlyStatusHandler() {
    return ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject));
  }

  public boolean isModified() {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    if (settings.FORCE_NON_EMPTY_COMMENT != myForceNonEmptyComment.isSelected()){
      return true;
    }
    if (settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT != myCbOfferToMoveChanges.isSelected()){
      return true;
    }

    if (settings.PERFORM_COMMIT_IN_BACKGROUND != myCbCommitInBackground.isSelected()) {
      return true;
    }

    if (settings.PERFORM_UPDATE_IN_BACKGROUND != myCbUpdateInBackground.isSelected()) {
      return true;
    }
    if (settings.PERFORM_EDIT_IN_BACKGROUND != myCbEditInBackground.isSelected()) {
      return true;
    }
    if (settings.PERFORM_ADD_REMOVE_IN_BACKGROUND != myCbAddRemoveInBackground.isSelected()) {
      return true;
    }

    if (!Comparing.equal(getFailedCommitConfirm(), settings.MOVE_TO_FAILED_COMMIT_CHANGELIST)) {
      return true;
    }

    if (getReadOnlyStatusHandler().getState().SHOW_DIALOG != myShowReadOnlyStatusDialog.isSelected()) {
      return true;
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      if (setting.getValue() != myPromptOptions.get(setting).isSelected()) return true;
    }

    if (getSelected(myOnFileAddingGroup) != getAddConfirmation().getValue()) return true;
    if (getSelected(myOnFileRemovingGroup) != getRemoveConfirmation().getValue()) return true;

    return false;
  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myForceNonEmptyComment.setSelected(settings.FORCE_NON_EMPTY_COMMENT);
    myCbOfferToMoveChanges.setSelected(settings.OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT);
    myShowReadOnlyStatusDialog.setSelected(getReadOnlyStatusHandler().getState().SHOW_DIALOG);
    myCbCommitInBackground.setSelected(settings.PERFORM_COMMIT_IN_BACKGROUND);
    myCbUpdateInBackground.setSelected(settings.PERFORM_UPDATE_IN_BACKGROUND);
    myCbEditInBackground.setSelected(settings.PERFORM_EDIT_IN_BACKGROUND);
    myCbAddRemoveInBackground.setSelected(settings.PERFORM_ADD_REMOVE_IN_BACKGROUND);
    if (settings.MOVE_TO_FAILED_COMMIT_CHANGELIST == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      myFailedCommitChangelistCombo.setSelectedIndex(0);
    }
    else if (settings.MOVE_TO_FAILED_COMMIT_CHANGELIST == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      myFailedCommitChangelistCombo.setSelectedIndex(1);
    }
    else {
      myFailedCommitChangelistCombo.setSelectedIndex(2);
    }

    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      myPromptOptions.get(setting).setSelected(setting.getValue());
    }

    selectInGroup(myOnFileAddingGroup, getAddConfirmation());
    selectInGroup(myOnFileRemovingGroup, getRemoveConfirmation());
  }

  private static void selectInGroup(final JRadioButton[] group, final VcsShowConfirmationOption confirmation) {
    final VcsShowConfirmationOption.Value value = confirmation.getValue();
    final int index;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(value) {
      case SHOW_CONFIRMATION: index = 0; break;
      case DO_ACTION_SILENTLY: index = 1; break;
      default: index = 2;
    }
    group[index].setSelected(true);
  }


  public JComponent getPanel() {
    return myPanel;
  }

  public void updateAvailableOptions(final Collection<AbstractVcs> activeVcses) {
    for (VcsShowOptionsSettingImpl setting : myPromptOptions.keySet()) {
      final JCheckBox checkBox = myPromptOptions.get(setting);
      checkBox.setEnabled(setting.isApplicableTo(activeVcses) || myProject.isDefault());
      if (!myProject.isDefault()) {
        checkBox.setToolTipText(VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(setting.getApplicableVcses())));
      }
    }

    if (!myProject.isDefault()) {
      final ProjectLevelVcsManagerEx vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject);
      final VcsShowConfirmationOptionImpl addConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.ADD);
      UIUtil.setEnabled(myAddConfirmationPanel, addConfirmation.isApplicableTo(activeVcses), true);
      myAddConfirmationPanel.setToolTipText(
        VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(addConfirmation.getApplicableVcses())));

      final VcsShowConfirmationOptionImpl removeConfirmation = vcsManager.getConfirmation(VcsConfiguration.StandardConfirmation.REMOVE);
      UIUtil.setEnabled(myRemoveConfirmationPanel, removeConfirmation.isApplicableTo(activeVcses), true);
      myRemoveConfirmationPanel.setToolTipText(
        VcsBundle.message("tooltip.text.action.applicable.to.vcses", composeText(removeConfirmation.getApplicableVcses())));
    }
  }

  private static String composeText(final List<AbstractVcs> applicableVcses) {
    final TreeSet<String> result = new TreeSet<String>();
    for (AbstractVcs abstractVcs : applicableVcses) {
      result.add(abstractVcs.getDisplayName());
    }
    return StringUtil.join(result, ", ");
  }
}

package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;

import javax.swing.*;

public class VcsGeneralConfigurationPanel {

  private JCheckBox myForceNonEmptyComment;
  private JCheckBox myReuseLastComment;
  private JCheckBox myPutFocusIntoComment;
  private JCheckBox myShowReadOnlyStatusDialog;

  private JCheckBox myShowUpdate;
  private JCheckBox myShowCommit;
  private JCheckBox myShowAdd;

  private JCheckBox myShowRemove;
  private JCheckBox myShowEdit;
  private JCheckBox myShowCheckout;

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

  }

  public void apply() throws ConfigurationException {

    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);

    settings.PUT_FOCUS_INTO_COMMENT = myPutFocusIntoComment.isSelected();
    settings.SAVE_LAST_COMMIT_MESSAGE = myReuseLastComment.isSelected();
    settings.FORCE_NON_EMPTY_COMMENT = myForceNonEmptyComment.isSelected();

    settings.SHOW_EDIT_DIALOG = myShowEdit.isSelected();
    settings.SHOW_CHECKOUT_OPTIONS = myShowCheckout.isSelected();
    settings.SHOW_ADD_OPTIONS = myShowAdd.isSelected();
    settings.SHOW_REMOVE_OPTIONS = myShowRemove.isSelected();
    settings.SHOW_UPDATE_OPTIONS = myShowUpdate.isSelected();
    settings.SHOW_CHECKIN_OPTIONS = myShowCommit.isSelected();

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

    return !(
      settings.SHOW_ADD_OPTIONS == myShowAdd.isSelected() &&
      settings.SHOW_REMOVE_OPTIONS == myShowRemove.isSelected() &&
      settings.SHOW_UPDATE_OPTIONS == myShowUpdate.isSelected() &&
      settings.SHOW_CHECKIN_OPTIONS == myShowCommit.isSelected()&&
      settings.SHOW_EDIT_DIALOG == myShowEdit.isSelected()      &&
      settings.SHOW_CHECKOUT_OPTIONS == myShowCheckout.isSelected()&&
      settings.ON_FILE_ADDING == getSelected(myOnFileAddingGroup)  &&
      settings.ON_FILE_REMOVING == getSelected(myOnFileRemovingGroup)
    );

  }

  public void reset() {
    VcsConfiguration settings = VcsConfiguration.getInstance(myProject);
    myPutFocusIntoComment.setSelected(settings.PUT_FOCUS_INTO_COMMENT);
    myReuseLastComment.setSelected(settings.SAVE_LAST_COMMIT_MESSAGE);
    myForceNonEmptyComment.setSelected(settings.FORCE_NON_EMPTY_COMMENT);
    myShowReadOnlyStatusDialog.setSelected(getReadOnlyStatusHandler().SHOW_DIALOG);

    myShowUpdate.setSelected(settings.SHOW_UPDATE_OPTIONS);
    myShowCommit.setSelected(settings.SHOW_CHECKIN_OPTIONS);
    myShowAdd.setSelected(settings.SHOW_ADD_OPTIONS);
    myShowRemove.setSelected(settings.SHOW_REMOVE_OPTIONS);
    myShowEdit.setSelected(settings.SHOW_EDIT_DIALOG);
    myShowCheckout.setSelected(settings.SHOW_CHECKOUT_OPTIONS);

    myOnFileAddingGroup[settings.ON_FILE_ADDING].setSelected(true);
    myOnFileRemovingGroup[settings.ON_FILE_REMOVING].setSelected(true);
  }

  private void createButtonGroup(JRadioButton[] group) {
    ButtonGroup buttonGroup = new ButtonGroup();
    for (int i = 0; i < group.length; i++) {
      buttonGroup.add(group[i]);
    }
  }


  public JComponent getPanel() {
    return myPanel;
  }
}

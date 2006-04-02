package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;

import javax.swing.*;

/**
 * @author max
 */
public class EditChangelistDialog extends DialogWrapper {
  private EditChangelistPanel myPanel;
  private final ChangeList myList;

  public EditChangelistDialog(Project project, ChangeList list) {
    super(project, true);
    myList = list;
    myPanel = new EditChangelistPanel();
    myPanel.setName(list.getName());
    myPanel.setDescription(list.getComment());

    setTitle(VcsBundle.message("changes.dialog.editchangelist.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel.getContent();
  }

  protected void doOKAction() {
    myList.setName(myPanel.getName());
    myList.setComment(myPanel.getDescription());
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.EditChangelistDialog";
  }
}

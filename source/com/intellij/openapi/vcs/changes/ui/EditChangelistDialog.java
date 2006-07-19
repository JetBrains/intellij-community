package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class EditChangelistDialog extends DialogWrapper {
  private EditChangelistPanel myPanel;
  private final Project myProject;
  private final LocalChangeList myList;

  public EditChangelistDialog(Project project, @NotNull LocalChangeList list) {
    super(project, true);
    myProject = project;
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
    String oldName = myList.getName();
    String oldComment = myList.getComment();

    if (!Comparing.equal(oldName, myPanel.getName()) && ChangeListManager.getInstance(myProject).findChangeList(myPanel.getName()) != null) {
      Messages.showErrorDialog(myPanel.getContent(),
                               VcsBundle.message("changes.dialog.editchangelist.error.already.exists", myPanel.getName()),
                               VcsBundle.message("changes.dialog.editchangelist.title"));
      return;
    }

    if (!Comparing.equal(oldName, myPanel.getName()) || !Comparing.equal(oldComment, myPanel.getDescription())) {
      myList.setName(myPanel.getName());
      myList.setComment(myPanel.getDescription());
      ChangeListManagerImpl.getInstanceImpl(myProject).notifyChangeListRenamed(myList, oldName);
    }
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPrefferedFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "VCS.EditChangelistDialog";
  }
}

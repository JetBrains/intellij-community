package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class EditChangelistDialog extends DialogWrapper {
  private final EditChangelistPanel myPanel;
  private final Project myProject;
  private final LocalChangeList myList;

  public EditChangelistDialog(Project project, @NotNull LocalChangeList list) {
    super(project, true);
    myProject = project;
    myList = list;
    myPanel = new EditChangelistPanel(((LocalChangeListImpl) list).getEditHandler(),
                                      new Consumer<Boolean>() {
                                        public void consume(final Boolean aBoolean) {
                                          setOKActionEnabled(Boolean.TRUE.equals(aBoolean));
                                        }
                                      });
    myPanel.setName(list.getName());
    myPanel.setDescription(list.getComment());
    myPanel.installSupport(project);
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

    if (!Comparing.equal(oldName, myPanel.getName(), true) || !Comparing.equal(oldComment, myPanel.getDescription(), true)) {
      final ChangeListManager clManager = ChangeListManager.getInstance(myProject);

      final String newName = myPanel.getName();
      if (! myList.getName().equals(newName)) {
        clManager.editName(myList.getName(), newName);
      }
      final String newDescription = myPanel.getDescription();
      if (! myList.getComment().equals(newDescription)) {
        clManager.editComment(myList.getName(), newDescription);
      }
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

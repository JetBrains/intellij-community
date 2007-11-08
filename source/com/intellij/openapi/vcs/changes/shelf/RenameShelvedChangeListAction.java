package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;

import java.util.List;

/**
 * @author yole
 */
public class RenameShelvedChangeListAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    assert changes != null;
    final ShelvedChangeList changeList = changes [0];
    String newName = Messages.showInputDialog(project, VcsBundle.message("shelve.changes.rename.prompt"),
                                              VcsBundle.message("shelve.changes.rename.title"),
                                              Messages.getQuestionIcon(), changeList.DESCRIPTION,
                                              new InputValidator() {
                                                public boolean checkInput(final String inputString) {
                                                  if (inputString.length() == 0) {
                                                    return false;
                                                  }
                                                  final List<ShelvedChangeList> list =
                                                    ShelveChangesManager.getInstance(project).getShelvedChangeLists();
                                                  for(ShelvedChangeList oldList: list) {
                                                    if (oldList != changeList && oldList.DESCRIPTION.equals(inputString)) {
                                                      return false;
                                                    }
                                                  }
                                                  return true;
                                                }

                                                public boolean canClose(final String inputString) {
                                                  return checkInput(inputString);
                                                }
                                              });
    if (newName != null && !newName.equals(changeList.DESCRIPTION)) {
      ShelveChangesManager.getInstance(project).renameChangeList(changeList, newName);
    }
  }

  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changes = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    e.getPresentation().setEnabled(project != null && changes != null && changes.length == 1);
  }
}

/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 21:57:44
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class RemoveChangeListAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    ChangeList[] lists = e.getData(DataKeys.CHANGE_LISTS);
    final boolean visible = canRemoveChangeLists(project, lists);
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP))
      e.getPresentation().setVisible(visible);
    else
      e.getPresentation().setEnabled(visible);
  }

  private static boolean canRemoveChangeLists(final Project project, final ChangeList[] lists) {
    if (project == null || lists == null || lists.length == 0) return false;
    for(ChangeList changeList: lists) {
      if (!(changeList instanceof LocalChangeList)) return false;
      LocalChangeList localChangeList = (LocalChangeList) changeList;
      if (localChangeList.isReadOnly()) return false;
      if (localChangeList.isDefault() && ChangeListManager.getInstance(project).getChangeLists().size() <= lists.length) return false;
    }
    return true;
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    final ChangeList[] lists = e.getData(DataKeys.CHANGE_LISTS);
    assert lists != null;
    int rc;

    for(ChangeList list: lists) {
      if (((LocalChangeList) list).isDefault()) {
        removeActiveChangeList(project, lists, list.getChanges().isEmpty());
        return;
      }
    }

    if (lists.length == 1) {
      final LocalChangeList list = (LocalChangeList)lists[0];
      rc = list.getChanges().size() == 0 ? DialogWrapper.OK_EXIT_CODE :
               Messages.showYesNoDialog(project,
                                        VcsBundle.message("changes.removechangelist.warning.text", list.getName()),
                                        VcsBundle.message("changes.removechangelist.warning.title"),
                                        Messages.getQuestionIcon());
    }
    else {
      rc = Messages.showYesNoDialog(project,
                                    VcsBundle.message("changes.removechangelist.multiple.warning.text", lists.length),
                                    VcsBundle.message("changes.removechangelist.warning.title"),
                                    Messages.getQuestionIcon());
    }

    if (rc == DialogWrapper.OK_EXIT_CODE) {
      for(ChangeList list: lists) {
        ChangeListManager.getInstance(project).removeChangeList((LocalChangeList) list);
      }
    }
  }

  private static void removeActiveChangeList(final Project project, final ChangeList[] lists, final boolean empty) {
    List<LocalChangeList> remainingLists = new ArrayList<LocalChangeList>(ChangeListManager.getInstance(project).getChangeLists());
    remainingLists.removeAll(Arrays.asList(lists));
    String[] names = new String[remainingLists.size()];
    for(int i=0; i<remainingLists.size(); i++) {
      names [i] = remainingLists.get(i).getName();
    }
    int nameIndex = Messages.showChooseDialog(project,
                                              empty ? VcsBundle.message("changes.remove.active.empty.prompt") : VcsBundle.message("changes.remove.active.prompt"),
                                              VcsBundle.message("changes.remove.active.title"),
                                              Messages.getQuestionIcon(), names, names [0]);
    if (nameIndex < 0) return;
    ChangeListManager.getInstance(project).setDefaultChangeList(remainingLists.get(nameIndex));
    for(ChangeList list: lists) {
      final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
      // we can't use findRealByCopy() because isDefault will differ between our copy and the real list
      changeListManager.removeChangeList(changeListManager.findChangeList(list.getName()));
    }
  }
}
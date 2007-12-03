/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:04:13
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.EditChangelistDialog;

public class RenameChangeListAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.actions.RenameChangeListAction");

  public void update(AnActionEvent e) {
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    final boolean visible =
      lists != null && lists.length == 1 && lists[0] instanceof LocalChangeList && !((LocalChangeList)lists[0]).isReadOnly();
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP))
      e.getPresentation().setVisible(visible);
    else
      e.getPresentation().setEnabled(visible);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    ChangeList[] lists = e.getData(VcsDataKeys.CHANGE_LISTS);
    assert lists != null;
    final LocalChangeList list = ChangeListManager.getInstance(project).findChangeList(lists[0].getName());
    if (list != null) {
      new EditChangelistDialog(project, list).show();
    }
    else {
      LOG.assertTrue(false, "Cannot find changelist " + lists [0].getName());
    }
  }
}
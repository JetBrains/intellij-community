/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:02:55
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;

public class SetDefaultChangeListAction extends AnAction {
  public SetDefaultChangeListAction() {
    super(VcsBundle.message("changes.action.setdefaultchangelist.text"),
          VcsBundle.message("changes.action.setdefaultchangelist.description"), IconLoader.getIcon("/actions/submit1.png"));
  }


  public void update(AnActionEvent e) {
    ChangeList[] lists = e.getData(DataKeys.CHANGE_LISTS);
    final boolean visible =
      lists != null && lists.length == 1 && lists[0] instanceof LocalChangeList && !((LocalChangeList)lists[0]).isDefault();
    if (e.getPlace().equals(ActionPlaces.CHANGES_VIEW_POPUP))
      e.getPresentation().setVisible(visible);
    else
      e.getPresentation().setEnabled(visible);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final ChangeList[] lists = e.getData(DataKeys.CHANGE_LISTS);
    assert lists != null;
    ChangeListManager.getInstance(project).setDefaultChangeList((LocalChangeList)lists[0]);
  }
}
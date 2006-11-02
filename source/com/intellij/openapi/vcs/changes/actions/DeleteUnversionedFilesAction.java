/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:14:56
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DeleteProvider;

public class DeleteUnversionedFilesAction extends AnAction {
  public DeleteUnversionedFilesAction() {
    super(IdeBundle.message("action.delete"), "",
          IconLoader.getIcon("/actions/cancel.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(DataKeys.DELETE_ELEMENT_PROVIDER);
    assert deleteProvider != null;
    deleteProvider.deleteElement(e.getDataContext());
  }

  @Override
  public void update(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(DataKeys.DELETE_ELEMENT_PROVIDER);
    e.getPresentation().setVisible(deleteProvider != null && deleteProvider.canDeleteElement(e.getDataContext()));
  }
}
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

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;

public class DeleteUnversionedFilesAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
    assert deleteProvider != null;
    deleteProvider.deleteElement(e.getDataContext());
  }

  @Override
  public void update(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
    e.getPresentation().setVisible(deleteProvider != null && deleteProvider.canDeleteElement(e.getDataContext()));
  }
}
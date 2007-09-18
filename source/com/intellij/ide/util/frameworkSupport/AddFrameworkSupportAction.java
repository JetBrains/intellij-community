/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class AddFrameworkSupportAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Module module = e.getData(DataKeys.MODULE_CONTEXT);
    if (module == null) return;
    
    AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
    if (dialog != null) {
      dialog.show();
    }
  }

  public void update(final AnActionEvent e) {
    Module module = e.getData(DataKeys.MODULE_CONTEXT);
    boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
    e.getPresentation().setEnabled(enable);
  }
}

package com.intellij.ide.util.frameworkSupport;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;

/**
 * @author nik
 */
public class AddFrameworkSupportAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) return;
    
    AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
    if (dialog != null) {
      dialog.show();
    }
  }

  public void update(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    boolean enable = module != null && AddFrameworkSupportDialog.isAvailable(module);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enable);
    }
    else {
      e.getPresentation().setEnabled(enable);
    }
  }
}

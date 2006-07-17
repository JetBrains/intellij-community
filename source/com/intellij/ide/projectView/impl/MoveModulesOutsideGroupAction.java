/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;

public class MoveModulesOutsideGroupAction extends AnAction {

  public MoveModulesOutsideGroupAction() {
    super(IdeBundle.message("action.move.module.outside.any.group"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    MoveModulesToGroupAction.doMove(modules, null, dataContext);
  }
}
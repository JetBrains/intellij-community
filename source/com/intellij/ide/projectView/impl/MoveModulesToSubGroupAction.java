/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;

public class MoveModulesToSubGroupAction extends MoveModulesToGroupAction {
  public MoveModulesToSubGroupAction(ModuleGroup moduleGroup) {
    super(moduleGroup, moduleGroup == null ? "New top level group..." : "To new sub group...");
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    String description = "Create new module group";
    presentation.setDescription(description);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    final String[] newGroup;
    if (myModuleGroup != null) {
      String message = "Specify name of " + myModuleGroup.presentableText() + " subgroup the "+whatToMove(modules)+" will be shown under.\n\n";
      String subgroup = Messages.showInputDialog(message, "Module Sub Group", Messages.getQuestionIcon());
      if (subgroup == null || "".equals(subgroup.trim())) return;
      newGroup = ArrayUtil.append(myModuleGroup.getGroupPath(), subgroup);
    }
    else {
      String message = "Specify group name the " + whatToMove(modules) + " will be shown under.\n\n";
      String group = Messages.showInputDialog(message, "Module Group", Messages.getQuestionIcon());
      if (group == null || "".equals(group.trim())) return;
      newGroup = new String[]{group};
    }

    doMove(modules, new ModuleGroup(newGroup));
  }
}
/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;

public class MoveModulesToSubGroupAction extends MoveModulesToGroupAction {
  public MoveModulesToSubGroupAction(ModuleGroup moduleGroup) {
    super(moduleGroup, moduleGroup == null ? IdeBundle.message("action.move.module.new.top.level.group") : IdeBundle.message("action.move.module.to.new.sub.group"));
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    String description = IdeBundle.message("action.description.create.new.module.group");
    presentation.setDescription(description);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    final String[] newGroup;
    if (myModuleGroup != null) {
      String message = IdeBundle.message("prompt.specify.name.of.module.subgroup", myModuleGroup.presentableText(), whatToMove(modules));
      String subgroup = Messages.showInputDialog(message, IdeBundle.message("title.module.sub.group"), Messages.getQuestionIcon());
      if (subgroup == null || "".equals(subgroup.trim())) return;
      newGroup = ArrayUtil.append(myModuleGroup.getGroupPath(), subgroup);
    }
    else {
      String message = IdeBundle.message("prompt.specify.module.group.name", whatToMove(modules));
      String group = Messages.showInputDialog(message, IdeBundle.message("title.module.group"), Messages.getQuestionIcon());
      if (group == null || "".equals(group.trim())) return;
      newGroup = new String[]{group};
    }

    doMove(modules, new ModuleGroup(newGroup), dataContext);
  }
}
/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

import org.jetbrains.annotations.Nullable;

public class MoveModuleToGroup extends ActionGroup {
  private final ModuleGroup myModuleGroup;

  public MoveModuleToGroup(ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
    setPopup(true);
  }

  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstantsEx.PROJECT);
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    boolean active = project != null && modules != null && modules.length != 0;
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(active);
    presentation.setText(myModuleGroup.presentableText());
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstantsEx.PROJECT);

    List<AnAction> result = new ArrayList<AnAction>();
    result.add(new MoveModulesToGroupAction(myModuleGroup, "To this group"));
    result.add(new MoveModulesToSubGroupAction(myModuleGroup));
    final Collection<ModuleGroup> children = myModuleGroup.childGroups(project);
    if (children.size() != 0) {
      result.add(Separator.getInstance());
    }
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      ModuleGroup moduleGroup = (ModuleGroup)iterator.next();
      result.add(new MoveModuleToGroup(moduleGroup));
    }

    return result.toArray(new AnAction[result.size()]);
  }
}
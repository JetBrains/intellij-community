/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;

import java.util.*;

public class MoveModuleToGroupTopLevel extends ActionGroup {
  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstantsEx.PROJECT);
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    boolean active = project != null && modules != null && modules.length != 0;
    e.getPresentation().setVisible(active);
  }

  public AnAction[] getChildren(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstantsEx.PROJECT);

    Module[] allModules = ModuleManager.getInstance(project).getModules();
    Set<String> topLevelGroupNames = new HashSet<String>();
    for (int i = 0; i < allModules.length; i++) {
      final Module child = allModules[i];
      String[] group = ModuleManager.getInstance(project).getModuleGroupPath(child);
      if (group != null) {
        topLevelGroupNames.add(group[0]);
      }
    }
    List<AnAction> result = new ArrayList<AnAction>();
    result.add(new MoveModulesOutsideGroupAction());
    result.add(new MoveModulesToSubGroupAction(null));
    result.add(Separator.getInstance());
    for (Iterator iterator = topLevelGroupNames.iterator(); iterator.hasNext();) {
      String name = (String)iterator.next();
      result.add(new MoveModuleToGroup(new ModuleGroup(new String[]{name})));
    }
    return result.toArray(new AnAction[result.size()]);
  }
}
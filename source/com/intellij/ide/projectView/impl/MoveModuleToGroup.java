/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MoveModuleToGroup extends ActionGroup {
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
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);

    String originalModuleGroup = null;
    boolean allModulesInSameGroup = true;
    for (int i = 0; i < modules.length; i++) {
      final Module child = modules[i];
      String group = ModuleManager.getInstance(project).getModuleGroup(child);
      if (originalModuleGroup == null) {
        originalModuleGroup = group;
      }
      if (!Comparing.strEqual(group, originalModuleGroup)) {
        allModulesInSameGroup = false;
      }
    }

    List<AnAction> result = new ArrayList<AnAction>();
    Module[] allModules = ModuleManager.getInstance(project).getModules();
    Set<String> groups = new HashSet<String>();
    for (int i = 0; i < allModules.length; i++) {
      final Module child = allModules[i];
      String group = ModuleManager.getInstance(project).getModuleGroup(child);
      if (group != null && !group.equals(originalModuleGroup) && groups.add(group)) {
        result.add(new MoveModulesToGroupAction(modules, group, group));
      }
    }
    result.add(Separator.getInstance());

    if (allModulesInSameGroup && originalModuleGroup != null) {
      result.add(new MoveModulesToGroupAction(modules, "", "Outside Any Group"));
    }
    result.add(new MoveModulesToGroupAction(modules, null, "New Group..."));
    return result.toArray(new AnAction[result.size()]);
  }
}
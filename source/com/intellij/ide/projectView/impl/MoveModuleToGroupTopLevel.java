/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MoveModuleToGroupTopLevel extends ActionGroup {
  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    boolean active = project != null && modules != null && modules.length != 0;
    e.getPresentation().setVisible(active);
  }

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    List<String> topLevelGroupNames = new ArrayList<String> (getTopLevelGroupNames(e.getDataContext()));
    Collections.sort ( topLevelGroupNames );

    List<AnAction> result = new ArrayList<AnAction>();
    result.add(new MoveModulesOutsideGroupAction());
    result.add(new MoveModulesToSubGroupAction(null));
    result.add(Separator.getInstance());
    for (String name : topLevelGroupNames) {
      result.add(new MoveModuleToGroup(new ModuleGroup(new String[]{name})));
    }
    return result.toArray(new AnAction[result.size()]);
  }

  private static Collection<String> getTopLevelGroupNames(final DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    final ModifiableModuleModel model = (ModifiableModuleModel)dataContext.getData(DataConstantsEx.MODIFIABLE_MODULE_MODEL);

    Module[] allModules;
    if ( model != null ) {
      allModules = model.getModules();
    } else {
      allModules = ModuleManager.getInstance(project).getModules();
    }

    Set<String> topLevelGroupNames = new HashSet<String>();
    for (final Module child : allModules) {
      String[] group;
      if ( model != null ) {
        group = model.getModuleGroupPath(child);
      } else {
        group = ModuleManager.getInstance(project).getModuleGroupPath(child);
      }
      if (group != null) {
        topLevelGroupNames.add(group[0]);
      }
    }
    return topLevelGroupNames;
  }
}
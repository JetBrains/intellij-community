/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;

public class MoveModulesToGroupAction extends AnAction {
  protected final ModuleGroup myModuleGroup;

  public MoveModulesToGroupAction(ModuleGroup moduleGroup, String title) {
    super(title);
    myModuleGroup = moduleGroup;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = getTemplatePresentation();
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);

    String description = "Move " + whatToMove(modules) + " to the group " + myModuleGroup.presentableText();
    presentation.setDescription(description);
  }

  protected static String whatToMove(Module[] modules) {
    return modules.length == 1 ? "module '" + modules[0].getName() + "'" : "modules";
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    doMove(modules, myModuleGroup);
  }

  protected static void doMove(final Module[] modules, final ModuleGroup group) {
    Project project = modules[0].getProject();
    for (int i = 0; i < modules.length; i++) {
      final Module module = modules[i];
      ModuleManagerImpl.getInstanceImpl(project).setModuleGroupPath(module, group == null ? null : group.getGroupPath());
    }
    ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID).updateFromRoot(true);
    if (group != null) {
      ProjectView.getInstance(project).selectModuleGroup(group, true);
    }
  }
}
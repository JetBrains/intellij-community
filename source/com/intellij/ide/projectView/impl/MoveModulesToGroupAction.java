/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class MoveModulesToGroupAction extends AnAction {
  private final Module[] myModules;
  private final String myGroupName;

  public MoveModulesToGroupAction(Module[] modules, String groupName, String title) {
    super(title);
    myModules = modules;
    myGroupName = groupName;
    Presentation presentation = getTemplatePresentation();
    String description = groupName == null ? "Create new module group"
                         : "Move "+whatToMove()+" to the group '"+groupName+"'";
    presentation.setDescription(description);
  }

  private String whatToMove() {
    String what = myModules.length == 1 ? "module '"+myModules[0].getName() +"'" : "modules";
    return what;
  }

  public void actionPerformed(AnActionEvent e) {
    String group = myGroupName;
    if (myGroupName == null) {
      String message = "Specify group the "+whatToMove()+" will be shown under.\n\n" +
                       "Leave the name blank to move module outside any group."
                       ;
      group = Messages.showInputDialog(message, "Module Group", Messages.getQuestionIcon());
      if (group == null) return;
    }
    Project project = myModules[0].getProject();
    if ("".equals(group.trim())) {
      group = null;
    }
    for (int i = 0; i < myModules.length; i++) {
      final Module module = myModules[i];
      ModuleManagerImpl.getInstanceImpl(project).setModuleGroup(module, group);
    }
    ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID).updateFromRoot(true);
    ProjectView.getInstance(project).getProjectViewPaneById(PackageViewPane.ID).updateFromRoot(true);
    if (group != null) {
      ProjectView.getInstance(project).selectModuleGroup(new ModuleGroup(group), true);
    }
  }
}
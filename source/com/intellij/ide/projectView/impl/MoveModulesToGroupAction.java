/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;

public class MoveModulesToGroupAction extends AnAction {
  protected final ModuleGroup myModuleGroup;

  public MoveModulesToGroupAction(ModuleGroup moduleGroup, String title) {
    super(title);
    myModuleGroup = moduleGroup;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = getTemplatePresentation();
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);

    String description = IdeBundle.message("message.move.modules.to.group", whatToMove(modules), myModuleGroup.presentableText());
    presentation.setDescription(description);
  }

  protected static String whatToMove(Module[] modules) {
    return modules.length == 1 ? IdeBundle.message("message.module", modules[0].getName()) : IdeBundle.message("message.modules");
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    doMove(modules, myModuleGroup, dataContext);
  }

  protected static void doMove(final Module[] modules, final ModuleGroup group, final DataContext dataContext) {
    Project project = modules[0].getProject();
    for (final Module module : modules) {
      ModifiableModuleModel model = (ModifiableModuleModel)dataContext.getData(DataConstantsEx.MODIFIABLE_MODULE_MODEL);
      if (model != null){
        model.setModuleGroupPath(module, group == null ? null : group.getGroupPath());
      } else {
        ModuleManagerImpl.getInstanceImpl(project).setModuleGroupPath(module, group == null ? null : group.getGroupPath());
      }
    }

    AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    pane.updateFromRoot(true);

    final ProjectRootConfigurable rootConfigurable = ProjectRootConfigurable.getInstance(project);
    if (rootConfigurable.updateProjectTree(modules, group)) { //inside project root editor
      if (group != null) {
        rootConfigurable.selectNodeInTree(group.toString());
      }
      else {
        rootConfigurable.selectNodeInTree(modules[0].getName());
      }
    }
    else {
      if (group != null) {
        pane.selectModuleGroup(group, true);
      }
      else {
        pane.selectModule(modules[0], true);
      }
    }
  }
}
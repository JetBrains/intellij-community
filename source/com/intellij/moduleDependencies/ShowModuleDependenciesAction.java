package com.intellij.moduleDependencies;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;

/**
 * User: anna
 * Date: Feb 9, 2005
 */
public class ShowModuleDependenciesAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }
    ModulesDependenciesPanel panel;
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (modules != null){
      panel = new ModulesDependenciesPanel(project, modules);
    } else {
      panel = new ModulesDependenciesPanel(project);
    }

    Content content = PeerFactory.getInstance().getContentFactory().createContent(panel,
                                                                                "Module Dependencies of " + project.getName(),
                                                                                false);
    panel.setContent(content);
    DependenciesAnalyzeManager.getInstance(project).addContent(content);
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }
}

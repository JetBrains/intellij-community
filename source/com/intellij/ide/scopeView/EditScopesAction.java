package com.intellij.ide.scopeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: 27-Jan-2006
 */
public class EditScopesAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.scopeView.EditScopesAction");

  public EditScopesAction() {
    getTemplatePresentation().setIcon(ScopeViewPane.ICON);
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    LOG.assertTrue(project != null);
    final String scopeName = ProjectView.getInstance(project).getCurrentProjectViewPane().getSubId();
    LOG.assertTrue(scopeName != null);
    final ScopeChooserConfigurable scopeChooserConfigurable = ScopeChooserConfigurable.getInstance(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, scopeChooserConfigurable, new Runnable(){
      public void run() {
        scopeChooserConfigurable.selectNodeInTree(scopeName);
      }
    });
  }
}

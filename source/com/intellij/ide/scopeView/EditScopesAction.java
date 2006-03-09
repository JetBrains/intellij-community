package com.intellij.ide.scopeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.scopeChooser.ScopeChooserDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;

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
    NamedScopesHolder holder = DependencyValidationManager.getInstance(project);
    if (holder.getScope(scopeName) == null){
      holder = NamedScopeManager.getInstance(project);
    }
    ScopeChooserDialog dlg = new ScopeChooserDialog(project, holder);
    dlg.show();
  }
}

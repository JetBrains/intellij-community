package com.intellij.ide.scopeView;

import com.intellij.ide.util.scopeChooser.ScopeChooserDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependencyValidationManager;

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
    ScopeChooserDialog dlg = new ScopeChooserDialog(project, DependencyValidationManager.getInstance(project));
    dlg.show();
  }
}

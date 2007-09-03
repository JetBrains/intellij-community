package com.intellij.moduleDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: Feb 9, 2005
 */
public class ShowModuleDependenciesAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null){
      return;
    }
    ModulesDependenciesPanel panel;
    AnalysisScope scope = new AnalysisScope(project);
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (modules != null){
      panel = new ModulesDependenciesPanel(project, modules);
      scope = new AnalysisScope(modules);
    } else {
      final PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_FILE);
      final Module module = element != null ? ModuleUtil.findModuleForPsiElement(element) : null;
      if (module != null && ModuleManager.getInstance(project).getModules().length > 1){
        MyModuleOrProjectScope dlg = new MyModuleOrProjectScope(module.getName());
        dlg.show();
        if (dlg.isOK()){
          if (!dlg.useProjectScope()){
            panel = new ModulesDependenciesPanel(project, new Module[]{module});
            scope = new AnalysisScope(module);
          } else {
            panel = new ModulesDependenciesPanel(project);
          }
        } else {
          return;
        }
      } else {
        panel = new ModulesDependenciesPanel(project);
      }
    }

    Content content = PeerFactory.getInstance().getContentFactory().createContent(panel,
                                                                                  AnalysisScopeBundle.message(
                                                                                    "module.dependencies.toolwindow.title",
                                                                                    StringUtil.decapitalize(scope.getDisplayName())),
                                                                                  false);
    content.setDisposer(panel);
    panel.setContent(content);
    DependenciesAnalyzeManager.getInstance(project).addContent(content);
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = DataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null);
  }

  private static class MyModuleOrProjectScope extends DialogWrapper{
    private JRadioButton myProjectScope;
    private JRadioButton myModuleScope;
    protected MyModuleOrProjectScope(String moduleName) {
      super(false);
      setTitle(AnalysisScopeBundle.message("module.dependencies.scope.dialog.title"));
      ButtonGroup group = new ButtonGroup();
      myProjectScope = new JRadioButton(AnalysisScopeBundle.message("module.dependencies.scope.dialog.project.button"));
      myModuleScope = new JRadioButton(AnalysisScopeBundle.message("module.dependencies.scope.dialog.module.button", moduleName));
      group.add(myProjectScope);
      group.add(myModuleScope);
      myProjectScope.setSelected(true);
      init();
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new GridLayout(2, 1));
      panel.add(myProjectScope);
      panel.add(myModuleScope);
      return panel;
    }

    public boolean useProjectScope(){
      return myProjectScope.isSelected();
    }
  }
}

package com.intellij.moduleDependencies;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }
    ModulesDependenciesPanel panel;
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    if (modules != null){
      panel = new ModulesDependenciesPanel(project, modules);
    } else {
      final PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_FILE);
      final Module module = element != null ? ModuleUtil.findModuleForPsiElement(element) : null;
      if (module != null && ModuleManager.getInstance(project).getModules().length > 1){
        MyModuleOrProjectScope dlg = new MyModuleOrProjectScope(module.getName());
        dlg.show();
        if (dlg.isOK()){
          if (!dlg.useProjectScope()){
            panel = new ModulesDependenciesPanel(project, new Module[]{module});
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

  private static class MyModuleOrProjectScope extends DialogWrapper{
    private JRadioButton myProjectScope;
    private JRadioButton myModuleScope;
    protected MyModuleOrProjectScope(String moduleName) {
      super(false);
      setTitle("Specify Analysis Scope");
      ButtonGroup group = new ButtonGroup();
      myProjectScope = new JRadioButton("Inspect the whole project");
      myProjectScope.setMnemonic('p');
      myModuleScope = new JRadioButton("Inspect module \'" + moduleName + "\'");
      myModuleScope.setMnemonic('m');
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

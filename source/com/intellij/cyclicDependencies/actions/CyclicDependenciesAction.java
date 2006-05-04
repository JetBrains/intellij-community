package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesAction extends AnAction{
  private final String myAnalysisVerb;
  private final String myAnalysisNoon;
  private final String myTitle;

  public CyclicDependenciesAction() {
    myAnalysisVerb = AnalysisScopeBundle.message("action.analyze.verb");
    myAnalysisNoon = AnalysisScopeBundle.message("action.analysis.noun");
    myTitle = AnalysisScopeBundle.message("action.cyclic.dependency.title");
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(
      getInspectionScope(event.getDataContext()) != null || event.getDataContext().getData(DataConstants.PSI_FILE) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Module module = (Module)dataContext.getData(DataConstants.MODULE);
    if (project != null) {
      PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
      if (psiFile != null && !(psiFile instanceof PsiJavaFile)) {
        return;
      }
      AnalysisScope scope = getInspectionScope(dataContext);
      if (scope == null || scope.getScopeType() != AnalysisScope.MODULES){
        ProjectModuleOrPackageDialog dlg = new ProjectModuleOrPackageDialog(module != null ? ModuleUtil.getModuleNameInReadAction(module) : null);
        dlg.show();
        if (!dlg.isOK()) return;
        if (dlg.isProjectScopeSelected()) {
          scope = getProjectScope(dataContext);
        }
        else {
          if (dlg.isModuleScopeSelected()) {
            scope = getModuleScope(dataContext);
          }
        }
      }

      FileDocumentManager.getInstance().saveAllDocuments();

      new CyclicDependenciesHandler(project, scope).analyze();
    }
  }


  private AnalysisScope getInspectionScope(final DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  private AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: package, project, module.
    Project projectContext = (Project)dataContext.getData(DataConstantsEx.PROJECT_CONTEXT);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    Module moduleContext = (Module)dataContext.getData(DataConstantsEx.MODULE_CONTEXT);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module [] modulesArray = (Module[])dataContext.getData(DataConstantsEx.MODULE_CONTEXT_ARRAY);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }

    PsiElement psiTarget = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    else if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs == null || dirs.length == 0) return null;
      return new AnalysisScope(pack);
    } else if (psiTarget != null){
      return null;
    }


    return getProjectScope(dataContext);
  }

  private AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope((Project)dataContext.getData(DataConstants.PROJECT));
  }

  private AnalysisScope getModuleScope(DataContext dataContext) {
    return new AnalysisScope((Module)dataContext.getData(DataConstants.MODULE));
  }

  private class ProjectModuleOrPackageDialog extends DialogWrapper {
    private String myModuleName;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;

    private JPanel myScopePanel;
    private JPanel myWholePanel;


    public ProjectModuleOrPackageDialog(String moduleName) {
      super(true);
      myModuleName = moduleName;
      init();
      setTitle(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.title", myTitle));
      setHorizontalStretch(1.75f);
      if (moduleName == null){
        myModuleButton.setVisible(false);
        myProjectButton.setSelected(true);
      } else {
        myModuleButton.setSelected(true);
      }
    }

    protected JComponent createCenterPanel() {
      myScopePanel.setBorder(IdeBorderFactory.createTitledBorder(AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoon)));
      myProjectButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.project.button", myAnalysisVerb));
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.module.button", myAnalysisVerb, myModuleName));
        group.add(myModuleButton);
      }
      return myWholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }

  }
}

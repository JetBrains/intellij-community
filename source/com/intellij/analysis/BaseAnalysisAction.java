package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BaseAnalysisAction extends AnAction {
  protected final String myTitle;
  protected final String myAnalysisVerb;
  protected final String myAnalysisNoon;

  protected BaseAnalysisAction(String title, String analysisVerb, String analysisNoon) {
    myTitle = title;
    myAnalysisVerb = analysisVerb;
    myAnalysisNoon = analysisNoon;
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
      AnalysisScope scope;
      PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
      if (psiFile != null && !(psiFile instanceof PsiJavaFile)) {
        scope = new AnalysisScope(psiFile);
      }
      else {
        scope = getInspectionScope(dataContext);
      }
      final boolean rememberScope = e.getPlace().equals(ActionPlaces.MAIN_MENU);
      final InspectionManagerEx.UIOptions uiOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions();
      BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog("Specify " + myTitle + " Scope",
                                                                  myAnalysisNoon + " scope",
                                                                  project,
                                                                  scope.getShortenName(),
                                                                  module != null && scope.getScopeType() != AnalysisScope.MODULE ? ModuleUtil.getModuleNameInReadAction(module) : null,
                                                                  scope.getScopeType() == AnalysisScope.PROJECT,
                                                                  rememberScope){
        @Nullable
        protected JComponent getAdditionalActionSettings(final Project project) {
          return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
        }
      };
      dlg.show();
      if (!dlg.isOK()) return;
      final int oldScopeType = uiOptions.SCOPE_TYPE;
      if (dlg.isProjectScopeSelected()) {
        scope = getProjectScope(dataContext);
        uiOptions.SCOPE_TYPE = AnalysisScope.PROJECT;
      }
      else {
        if (dlg.isModuleScopeSelected()) {
          scope = getModuleScope(dataContext);
          uiOptions.SCOPE_TYPE = AnalysisScope.MODULE;
        } else {
          uiOptions.SCOPE_TYPE = AnalysisScope.FILE;//just not project scope
        }
      }
      if (!rememberScope){
        uiOptions.SCOPE_TYPE = oldScopeType;
      }
      uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
      scope.setIncludeTestSource(dlg.isInspectTestSources());
      FileDocumentManager.getInstance().saveAllDocuments();

      analyze(project, scope);
    }
  }

  protected abstract void analyze(Project project, AnalysisScope scope);

  private AnalysisScope getInspectionScope(final DataContext dataContext) {
    if (dataContext.getData(DataConstants.PROJECT) == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  private AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: file, directory, package, project, module.
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
    PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    if (psiFile != null) {
      return psiFile instanceof PsiJavaFile ? new AnalysisScope(psiFile) : null;
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
    }
    else if (psiTarget != null) {
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

  @Nullable
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog){
    return null;
  }

}

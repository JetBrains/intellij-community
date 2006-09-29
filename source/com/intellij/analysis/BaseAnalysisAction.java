package com.intellij.analysis;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.UIOptions;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseAnalysisAction extends AnAction {
  protected final String myTitle;
  protected final String myAnalysisNoon;
  private static final Logger LOG = Logger.getInstance("#com.intellij.analysis.BaseAnalysisAction");

  protected BaseAnalysisAction(String title, String analysisNoon) {
    myTitle = title;
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
      if (psiFile != null) {
        scope = new AnalysisScope(psiFile);
      }
      else {
        scope = getInspectionScope(dataContext);
      }
      LOG.assertTrue(scope != null);
      if (scope.getScopeType() == AnalysisScope.VIRTUAL_FILES){
        FileDocumentManager.getInstance().saveAllDocuments();
        analyze(project, scope);
        return;
      }
      final boolean rememberScope = e.getPlace().equals(ActionPlaces.MAIN_MENU);
      final UIOptions uiOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions();
      BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", myTitle),
                                                                  AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoon),
                                                                  project,
                                                                  scope.getShortenName(),
                                                                  module != null && scope.getScopeType() != AnalysisScope.MODULE ? ModuleUtil
                                                                    .getModuleNameInReadAction(module) : null,
                                                                  rememberScope){
        @Nullable
        protected JComponent getAdditionalActionSettings(final Project project) {
          return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
        }


        protected void doHelpAction() {
          HelpManager.getInstance().invokeHelp(getHelpTopic());
        }

        protected Action[] createActions() {
          return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
        }
      };
      dlg.show();
      if (!dlg.isOK()) {
        canceled();
        return;
      }
      final int oldScopeType = uiOptions.SCOPE_TYPE;
      scope = dlg.getScope(uiOptions, scope, project, module);
      if (!rememberScope){
        uiOptions.SCOPE_TYPE = oldScopeType;
      }
      uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
      scope.setIncludeTestSource(dlg.isInspectTestSources());
      FileDocumentManager.getInstance().saveAllDocuments();

      analyze(project, scope);
    }
  }

  protected void canceled() {
  }

  @NonNls
  protected String getHelpTopic() {
    return "editing.analyzeDependencies.dialog";
  }

  protected abstract void analyze(Project project, AnalysisScope scope);

  @Nullable
  private static AnalysisScope getInspectionScope(final DataContext dataContext) {
    if (dataContext.getData(DataConstants.PROJECT) == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private static AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
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
    if (psiFile != null && psiFile.getManager().isInProject(psiFile)) {
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
      if (!pack.getManager().isInProject(pack)) return null;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs.length == 0) return null;
      return new AnalysisScope(pack);
    }
    else if (psiTarget != null) {
      return null;
    }

    final VirtualFile[] virtualFiles = (VirtualFile[])dataContext.getData(DataConstantsEx.VIRTUAL_FILE_ARRAY);
    if (virtualFiles != null) { //analyze on selection
      final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      Set<VirtualFile> files = new HashSet<VirtualFile>();
      for (VirtualFile vFile : virtualFiles) {
        if (fileIndex.isInContent(vFile)) {
          traverseDirectory(vFile, files);
        }
      }
      return new AnalysisScope(project, files);
    }
    return getProjectScope(dataContext);
  }

  private static AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope((Project)dataContext.getData(DataConstants.PROJECT));
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog){
    return null;
  }

  private static void traverseDirectory(VirtualFile vFile, Set<VirtualFile> files){
    if (vFile.isDirectory()){
      final VirtualFile[] virtualFiles = vFile.getChildren();
      for (VirtualFile virtualFile : virtualFiles) {
        traverseDirectory(virtualFile, files);
      }
    } else {
      files.add(vFile);
    }
  }
}

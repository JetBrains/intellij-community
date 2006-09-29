package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

public class ForwardDependenciesBuilder extends DependenciesBuilder {

  public ForwardDependenciesBuilder(Project project, AnalysisScope scope) {
    super(project, scope);
  }

  public String getRootNodeNameInUsageView(){
    return AnalysisScopeBundle.message("forward.dependencies.usage.view.root.node.text");
  }

  public String getInitialUsagesPosition(){
    return AnalysisScopeBundle.message("forward.dependencies.usage.view.initial.text");
  }

  public boolean isBackward(){
    return false;
  }

  public void analyze() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.startBatchFilesProcessingMode();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    try {
      getScope().accept(new PsiRecursiveElementVisitor() {
        public void visitFile(final PsiFile file) {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) {
            if (indicator.isCanceled()) {
              throw new ProcessCanceledException();
            }
            indicator.setText(AnalysisScopeBundle.message("package.dependencies.progress.text"));
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              indicator.setText2(VfsUtil.calcRelativeToProjectPath(virtualFile, getProject()));
            }
            indicator.setFraction(((double)++ myFileCount) / myTotalFileCount);
          }

          final FileViewProvider viewProvider = file.getViewProvider();
          if (viewProvider.getBaseLanguage() != file.getLanguage()) return;

          final Set<PsiFile> fileDeps = new HashSet<PsiFile>();
          getDependencies().put(file, fileDeps);
          analyzeFileDependencies(file, new DependencyProcessor() {
            public void process(PsiElement place, PsiElement dependency) {
              PsiFile dependencyFile = dependency.getContainingFile();
              if (dependencyFile != null) {
                if (viewProvider == dependencyFile.getViewProvider()) return;
                if (dependencyFile.isPhysical()) {
                  final VirtualFile virtualFile = dependencyFile.getVirtualFile();
                  if (virtualFile != null && (fileIndex.isInContent(virtualFile)
                                              || fileIndex.isInLibraryClasses(virtualFile)
                                              || fileIndex.isInLibrarySource(virtualFile))) {
                    fileDeps.add(dependencyFile);
                  }
                }
              }
            }
          });
          psiManager.dropResolveCaches();
        }
      });
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
  }

}
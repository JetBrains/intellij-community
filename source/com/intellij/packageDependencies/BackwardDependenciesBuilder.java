package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesBuilder extends DependenciesBuilder {

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope) {
    super(project, scope);
  }

  public String getRootNodeNameInUsageView() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.root.node.text");
  }

  public String getInitialUsagesPosition() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.initial.text");
  }

  public boolean isBackward(){
    return true;
  }

  public void analyze() {
    final AnalysisScope[] scopes = getScope().getNarrowedComplementaryScope(getProject());
    final DependenciesBuilder[] builders = new DependenciesBuilder[scopes.length];
    int totalCount = 0;
    for (AnalysisScope scope : scopes) {
      totalCount += scope.getFileCount();
    }
    final int finalTotalFilesCount = totalCount;
    totalCount = 0;
    for (int i = 0; i < scopes.length; i++) {
      AnalysisScope scope = scopes[i];
      builders[i] = new ForwardDependenciesBuilder(getProject(), scope);
      builders[i].setInitialFileCount(totalCount);
      builders[i].setTotalFileCount(finalTotalFilesCount);
      builders[i].analyze();
      totalCount += scope.getFileCount();
    }

    final PsiManager psiManager = PsiManager.getInstance(getProject());
    psiManager.startBatchFilesProcessingMode();
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
            indicator.setFraction(((double) ++myFileCount) / getScope().getFileCount());
          }
          for (DependenciesBuilder builder : builders) {
            final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
            for (final PsiFile psiFile : dependencies.keySet()) {
              if (dependencies.get(psiFile).contains(file)) {
                Set<PsiFile> fileDeps = getDependencies().get(file);
                if (fileDeps == null) {
                  fileDeps = new HashSet<PsiFile>();
                  getDependencies().put(file, fileDeps);
                }
                fileDeps.add(psiFile);
              }
            }
          }
          psiManager.dropResolveCaches();
        }
      });
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
  }
}

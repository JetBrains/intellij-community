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
  private final AnalysisScope[] myForwardScopes;

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope) {
    this(project, scope, null);
  }

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope, final AnalysisScope scopeOfInterest) {
    super(project, scope, scopeOfInterest);
    myForwardScopes = getScope().getNarrowedComplementaryScope(getProject());
    int totalCount = 0;
    for (AnalysisScope forwardScope : myForwardScopes) {
      totalCount += forwardScope.getFileCount();
    }
    myFileCount = totalCount;
    myTotalFileCount = totalCount + scope.getFileCount();
  }

  public String getRootNodeNameInUsageView() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.root.node.text");
  }

  public String getInitialUsagesPosition() {
    return AnalysisScopeBundle.message("backward.dependencies.usage.view.initial.text");
  }

  public boolean isBackward() {
    return true;
  }

  public void analyze() {
    final DependenciesBuilder[] builders = new DependenciesBuilder[myForwardScopes.length];
    int totalCount = 0;
    for (int i = 0; i < myForwardScopes.length; i++) {
      AnalysisScope scope = myForwardScopes[i];
      builders[i] = new ForwardDependenciesBuilder(getProject(), scope, getScopeOfInterest());
      builders[i].setInitialFileCount(totalCount);
      builders[i].setTotalFileCount(myTotalFileCount);
      builders[i].analyze();
      totalCount += scope.getFileCount();

      subtractScope(builders[i], getScope());
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
            final int fileCount = getScope().getFileCount();
            if (fileCount > 0) {
              indicator.setFraction(((double)++myFileCount) / myTotalFileCount);
            }
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

  private static void subtractScope(final DependenciesBuilder builders, final AnalysisScope scope) {
    final Map<PsiFile, Set<PsiFile>> dependencies = builders.getDependencies();

    Set<PsiFile> excluded = new HashSet<PsiFile>();

    for (final PsiFile psiFile : dependencies.keySet()) {
      if (scope.contains(psiFile)) {
        excluded.add(psiFile);
      }
    }

    for ( final PsiFile psiFile : excluded ) {
      dependencies.remove(psiFile);
    }
  }
}

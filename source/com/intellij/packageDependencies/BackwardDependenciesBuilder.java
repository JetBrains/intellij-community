package com.intellij.packageDependencies;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.analysis.AnalysisScope;
import com.intellij.psi.*;

import java.util.*;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesBuilder extends DependenciesBuilder {

  public BackwardDependenciesBuilder(final Project project, final AnalysisScope scope) {
    super(project, scope);
  }

  public String getRootNodeNameInUsageView() {
    return "Usages of the left tree scope selection in the right tree scope selection";
  }

  public String getInitialUsagesPosition() {
    return "Select where to search in right tree and what to search in left tree.";
  }

  public boolean isBackward(){
    return true;
  }

  public void analyze() {
    final AnalysisScope[] scopes = getScope().getNarrowedComplementaryScope(getProject());
    final DependenciesBuilder[] builders = new DependenciesBuilder[scopes.length];
    int totalCount = 0;
    for (int i = 0; i < scopes.length; i++) {
      AnalysisScope scope = scopes[i];
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
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        public void visitFile(final PsiFile file) {
          ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
          if (indicator != null) {
            if (indicator.isCanceled()) {
              throw new ProcessCanceledException();
            }
            indicator.setText("Analyzing package dependencies");
            indicator.setText2(file.getVirtualFile().getPresentableUrl());
            indicator.setFraction(((double) ++myFileCount) / getScope().getFileCount());
          }
          for (int i = 0; i < builders.length; i++) {
            final Map<PsiFile, Set<PsiFile>> dependencies = builders[i].getDependencies();
            for (Iterator<PsiFile> iterator = dependencies.keySet().iterator(); iterator.hasNext();) {
              final PsiFile psiFile = iterator.next();
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

package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.util.ArrayUtil;

import java.util.*;

public class ForwardDependenciesBuilder extends DependenciesBuilder {

  public ForwardDependenciesBuilder(Project project, AnalysisScope scope) {
    super(project, scope);
  }

  public String getRootNodeNameInUsageView(){
    return "Usages of the right tree scope selection in the left tree scope selection";
  }

  public String getInitialUsagesPosition(){
    return "Select where to search in left tree and what to search in right tree.";
  }

  public boolean isBackward(){
    return false;
  }

  public void analyze() {
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
            indicator.setFraction(((double)++ myFileCount) / myTotalFileCount);
          }

          final Set<PsiFile> fileDeps = new HashSet<PsiFile>();
          getDependencies().put(file, fileDeps);
          analyzeFileDependencies(file, new DependencyProcessor() {
            public void process(PsiElement place, PsiElement dependency) {
              PsiFile dependencyFile = dependency.getContainingFile();
              if (dependencyFile != null && dependencyFile.isPhysical()) {
                fileDeps.add(dependencyFile);
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
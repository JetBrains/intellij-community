package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

import java.util.*;

public class DependenciesBuilder {
  private Project myProject;
  private final AnalysisScope myScope;
  private final Map<PsiFile, Set<PsiFile>> myDependencies = new HashMap<PsiFile, Set<PsiFile>>();
  private final int myTotalFileCount;
  private int myFileCount = 0;

  public DependenciesBuilder(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
    myTotalFileCount = scope.getFileCount();
  }

  public AnalysisScope getScope() {
    return myScope;
  }

  public void analyze() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    psiManager.startBatchFilesProcessingMode();
    try {
      myScope.accept(new PsiRecursiveElementVisitor() {
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
            indicator.setFraction(((double)++myFileCount) / myTotalFileCount);
          }

          final Set<PsiFile> fileDeps = new HashSet<PsiFile>();
          myDependencies.put(file, fileDeps);
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

  public Map<PsiFile, Set<PsiFile>> getDependencies() {
    return myDependencies;
  }

  public Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> getIllegalDependencies() {
    Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> result = new HashMap<PsiFile, Map<DependencyRule, Set<PsiFile>>>();
    DependencyValidationManager validator = DependencyValidationManager.getInstance(myProject);
    for (Iterator<PsiFile> iterator = myDependencies.keySet().iterator(); iterator.hasNext();) {
      PsiFile file = iterator.next();
      Set<PsiFile> deps = myDependencies.get(file);
      Map<DependencyRule, Set<PsiFile>> illegal = null;
      for (Iterator<PsiFile> depsIterator = deps.iterator(); depsIterator.hasNext();) {
        PsiFile dependency = depsIterator.next();
        final DependencyRule rule = validator.getViolatorDependencyRule(file, dependency);
        if (rule != null) {
          if (illegal == null) {
            illegal = new HashMap<DependencyRule, Set<PsiFile>>();
            result.put(file, illegal);
          }
          Set<PsiFile> illegalFilesByRule = illegal.get(rule);
          if (illegalFilesByRule == null) {
            illegalFilesByRule = new HashSet<PsiFile>();
          }
          illegalFilesByRule.add(dependency);
          illegal.put(rule, illegalFilesByRule);
        }
      }
    }
    return result;
  }

  public void analyzeFileDependencies(PsiFile file, DependencyProcessor processor) {
    file.accept(new DependenciesWalker(processor));
  }

  public interface DependencyProcessor {
    void process(PsiElement place, PsiElement dependency);
  }

  private static class DependenciesWalker extends PsiRecursiveElementVisitor {
    private final DependencyProcessor myProcessor;

    public DependenciesWalker(DependencyProcessor processor) {
      myProcessor = processor;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitElement(PsiElement element) {
      super.visitElement(element);
      PsiReference[] refs = element.getReferences();
      for (int i = 0; i < refs.length; i++) {
        PsiReference ref = refs[i];
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod != null) {
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null) {
          PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
          if (psiClass != null) {
            myProcessor.process(expression, psiClass);
          }
        }
      }
    }
  }
}
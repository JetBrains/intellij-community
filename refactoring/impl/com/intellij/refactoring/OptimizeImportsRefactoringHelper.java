package com.intellij.refactoring;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Set;

public class OptimizeImportsRefactoringHelper implements RefactoringHelper<Set<PsiJavaFile>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.OptimizeImportsRefactoringHelper");

  public Set<PsiJavaFile> prepareOperation(final UsageInfo[] usages) {
    Set<PsiJavaFile> javaFiles = new HashSet<PsiJavaFile>();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null) {
        final PsiFile file = element.getContainingFile();
        if (file instanceof PsiJavaFile) {
          javaFiles.add((PsiJavaFile)file);
        }
      }
    }
    return javaFiles;
  }

  public void performOperation(final Project project, final Set<PsiJavaFile> javaFiles) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    for (PsiJavaFile file : javaFiles) {
      try {
        if (file.isValid() && file.getVirtualFile() != null) {
          styleManager.removeRedundantImports(file);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}

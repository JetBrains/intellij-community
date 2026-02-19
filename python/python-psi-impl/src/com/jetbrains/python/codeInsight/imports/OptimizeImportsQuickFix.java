// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;


public class OptimizeImportsQuickFix implements LocalQuickFix, IntentionAction, HighPriorityAction {

  @Override
  public @NotNull String getText() {
    return getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.optimize.imports");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return psiFile instanceof PyFile;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    optimizeImports(project, psiFile);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) {  // stale PSI
      return;
    }
    final PsiFile file = element.getContainingFile();
    optimizeImports(project, file);
  }

  private void optimizeImports(final Project project, final PsiFile file) {
    ImportOptimizer optimizer = new PyImportOptimizer();
    final Runnable runnable = optimizer.processFile(file);
    WriteCommandAction.writeCommandAction(project, file).withName(getFamilyName()).run(() -> runnable.run());
  }
}

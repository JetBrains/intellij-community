// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;


public class AutoImportHintAction implements LocalQuickFix, HintAction, HighPriorityAction {
  private final @NotNull AutoImportQuickFix myDelegate;

  public AutoImportHintAction(@NotNull AutoImportQuickFix delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    List<? extends ImportCandidateHolder> imports = myDelegate.getCandidates();
    if (!PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP ||
        HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true) ||
        imports.isEmpty()) {
      return false;
    }
    final PsiElement element = myDelegate.getStartElement();
    PyPsiUtils.assertValid(element);
    if (element == null || !element.isValid()) {
      return false;
    }
    final PyElement pyElement = as(element, PyElement.class);
    String initialName = myDelegate.getNameToImport();
    if (pyElement == null || !initialName.equals(pyElement.getName())) {
      return false;
    }
    final PsiReference reference = myDelegate.findOriginalReference(element);
    if (reference == null || AutoImportQuickFix.isResolved(reference)) {
      return false;
    }
    if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
      return false; // we cannot be qualified
    }

    final String message = ShowAutoImportPass.getMessage(
      imports.size() > 1,
      DaemonBundle.message("symbol"),
      ImportCandidateHolder.getQualifiedName(initialName, imports.get(0).getPath(), imports.get(0).getImportElement())
    );
    final ImportFromExistingAction action = myDelegate.createAction(element);
    HintManager.getInstance().showQuestionHint(
      editor, message,
      element.getTextOffset(),
      element.getTextRange().getEndOffset(), action);
    return true;
  }

  @Override
  public @NotNull String getText() {
    return myDelegate.getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myDelegate.isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    myDelegate.invoke();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getName() {
    return myDelegate.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return myDelegate.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    myDelegate.applyFix(project, descriptor);
  }

  public @NotNull AutoImportQuickFix getDelegate() {
    return myDelegate;
  }
}

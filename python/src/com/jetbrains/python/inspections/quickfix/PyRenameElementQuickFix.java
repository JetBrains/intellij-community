// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.psi.util.PsiEditorUtilBase#findEditorByPsiElement
 */
public class PyRenameElementQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement implements LocalQuickFix {

  public PyRenameElementQuickFix(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyBundle.message("QFIX.FAMILY.NAME.rename.element");
  }

  @Override
  public @NotNull String getText() {
    return PyBundle.message("QFIX.NAME.rename.element");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiElement element = PyQuickFixUtil.dereference(startElement);
    if (element == null) {
      return;
    }
    final PsiNameIdentifierOwner nameOwner = element instanceof PsiNameIdentifierOwner ?
                                             (PsiNameIdentifierOwner)element :
                                             PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner.class, true);
    if (nameOwner == null || editor == null) {
      return;
    }
    if (checkLocalScope(element) != null && (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression)) {
      new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
    }
    else {
      PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
    }
  }

  protected @Nullable PsiElement checkLocalScope(PsiElement element) {
    final SearchScope searchScope = PsiSearchHelper.getInstance(element.getProject()).getUseScope(element);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean availableInBatchMode() {
    return false;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }
}

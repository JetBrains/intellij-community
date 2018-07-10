// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.psi.util.PsiUtilBase#findEditor
 */
public class PyRenameElementQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  public PyRenameElementQuickFix(@Nullable PsiElement element) {
    super(element);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.rename.element");
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("QFIX.NAME.rename.element");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
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
    if (nameOwner != null && editor != null) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        renameInUnitTestMode(project, nameOwner, editor);
      }
      else {
        if (checkLocalScope(element) != null && (nameOwner instanceof PyNamedParameter || nameOwner instanceof PyTargetExpression)) {
          new VariableInplaceRenamer(nameOwner, editor).performInplaceRename();
        }
        else {
          PsiElementRenameHandler.invoke(nameOwner, project, ScopeUtil.getScopeOwner(nameOwner), editor);
        }
      }
    }
  }

  @Nullable
  protected PsiElement checkLocalScope(PsiElement element) {
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

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return file;
  }

  private static void renameInUnitTestMode(@NotNull Project project, @NotNull PsiNameIdentifierOwner nameOwner,
                                           @Nullable Editor editor) {
    final PsiElement substitution = RenamePsiElementProcessor.forElement(nameOwner).substituteElementToRename(nameOwner, editor);
    if (substitution != null) {
      new RenameProcessor(project, substitution, "a", false, false).run();
    }
  }
}

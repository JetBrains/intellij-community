/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyIntroduceConstantHandler extends IntroduceHandler {
  public PyIntroduceConstantHandler() {
    super(new ConstantValidator(), PyPsiBundle.message("refactoring.introduce.constant.dialog.title"));
  }

  @Override
  protected PsiElement replaceExpression(PsiElement expression, PyExpression newExpression, IntroduceOperation operation) {
    if (PsiTreeUtil.getParentOfType(expression, ScopeOwner.class) instanceof PyFile) {
      return super.replaceExpression(expression, newExpression, operation);
    }
    return PyReplaceExpressionUtil.replaceExpression(expression, newExpression);
  }

  @Override
  protected PsiElement addDeclaration(@NotNull PsiElement expression,
                                      @NotNull PsiElement declaration,
                                      @NotNull IntroduceOperation operation) {
    PsiElement containingFile = expression.getContainingFile();
    assert containingFile instanceof PyFile;
    PsiElement initialPosition = AddImportHelper.getFileInsertPosition((PyFile)containingFile);

    List<PsiElement> sameFileRefs = collectReferencedDefinitionsInSameFile(operation.getElement(), operation.getFile());
    PsiElement maxPosition = getLowermostTopLevelStatement(sameFileRefs);

    if (maxPosition == null) {
      return containingFile.addBefore(declaration, initialPosition);
    }

    assert PyUtil.isTopLevel(maxPosition);
    return containingFile.addAfter(declaration, maxPosition);
  }

  @Override
  protected Collection<String> generateSuggestedNames(@NotNull PyExpression expression) {
    Collection<String> names = new HashSet<>();
    for (String name : super.generateSuggestedNames(expression)) {
      names.add(StringUtil.toUpperCase(name));
    }
    return names;
  }

  @Override
  protected boolean isValidIntroduceContext(PsiElement element) {
    return super.isValidIntroduceContext(element) || PsiTreeUtil.getParentOfType(element, PyParameterList.class) != null;
  }

  @Override
  protected boolean checkEnabled(@NotNull IntroduceOperation operation) {
    PsiElement selectionElement = getOriginalSelectionCoveringElement(operation.getElement());

    PsiFile containingFile = selectionElement.getContainingFile();
    if (!(containingFile instanceof PyFile)) return false;

    Editor editor = operation.getEditor();
    if (editor == null) return false;

    List<PsiElement> sameFileRefs = collectReferencedDefinitionsInSameFile(operation.getElement(), operation.getFile());
    if (!ContainerUtil.all(sameFileRefs, it -> PyUtil.isTopLevel(it))) return false;
    PsiElement maxPosition = getLowermostTopLevelStatement(sameFileRefs);
    if (maxPosition == null) return true;
    return PsiUtilCore.compareElementsByPosition(maxPosition, selectionElement) <= 0 &&
           !PsiTreeUtil.isAncestor(maxPosition, selectionElement, false);
  }

  private static @Nullable PsiElement getLowermostTopLevelStatement(@NotNull List<PsiElement> elements) {
    return StreamEx.of(elements)
      .map(it -> PyPsiUtils.getParentRightBefore(it, it.getContainingFile()))
      .select(PyStatement.class)
      .max(PsiUtilCore::compareElementsByPosition)
      .orElse(null);
  }

  @Override protected void showCanNotIntroduceErrorHint(@NotNull Project project, @NotNull Editor editor) {
    String message =
      RefactoringBundle.getCannotRefactorMessage(PyPsiBundle.message("refactoring.introduce.constant.cannot.extract.selected.expression"));
    CommonRefactoringUtil.showErrorHint(project, editor, message, myDialogTitle, getHelpId());
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceConstant";
  }

  @Override
  protected String getRefactoringId() {
    return "refactoring.python.introduce.constant";
  }
}

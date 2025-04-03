// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.invertBoolean;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.invertBoolean.InvertBooleanDelegate;
import com.intellij.refactoring.rename.RenameProcessor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class PyInvertBooleanDelegate extends InvertBooleanDelegate {
  @Override
  public boolean isVisibleOnElement(@NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    final VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
    if (virtualFile != null &&
        ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
      return false;
    }
    if (element instanceof PyTargetExpression || element instanceof PyNamedParameter) {
      return true;
    }
    return isBooleanLiteral(element.getParent());
  }

  @Override
  public boolean isAvailableOnElement(@NotNull PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile != null && ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) return false;
    if (element instanceof PyTargetExpression) {
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue == null) return false;
        return isBooleanLiteral(assignedValue);
      }
    }
    if (element instanceof PyNamedParameter) {
      final PyExpression defaultValue = ((PyNamedParameter)element).getDefaultValue();
      if (defaultValue != null && isBooleanLiteral(defaultValue)) {
        return true;
      }
    }
    return isBooleanLiteral(element.getParent());
  }

  @Override
  public @Nullable PsiElement adjustElement(PsiElement element, Project project, Editor editor) {
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement != null) {
      return assignmentStatement.getTargets()[0];
    }
    else if (element instanceof PyNamedParameter) {
      return element;
    }
    return null;
  }

  @Override
  public void collectRefElements(PsiElement psiElement,
                                 @Nullable RenameProcessor renameProcessor,
                                 @NotNull String newName,
                                 Collection<? super PsiElement> elementsToInvert) {
    final Collection<PsiReference> refs = ReferencesSearch.search(psiElement).findAll();

    for (PsiReference ref : refs) {
      final PsiElement refElement = ref.getElement();
      if (!collectElementsToInvert(psiElement, refElement, elementsToInvert)) {
        collectForeignElementsToInvert(psiElement, refElement, PythonLanguage.getInstance(), elementsToInvert);
      }
    }
  }

  @Override
  public PsiElement getElementToInvert(PsiElement namedElement, PsiElement element) {
    if (element instanceof PyTargetExpression target) {
      final PyAssignmentStatement parent = PsiTreeUtil.getParentOfType(target, PyAssignmentStatement.class);
      if (parent != null && parent.getTargets().length == 1) {
        final PyExpression value = parent.getAssignedValue();
        if (value != null)
          return value;
      }
    }
    else if (element.getParent() instanceof PyPrefixExpression) {
      return element.getParent();
    }
    else if (element instanceof PyReferenceExpression) {
      return element;
    }
    return null;
  }

  @Override
  public void invertElementInitializer(PsiElement psiElement) {
    if (psiElement instanceof PyNamedParameter) {
      final PyExpression defaultValue = ((PyNamedParameter)psiElement).getDefaultValue();
      if (defaultValue != null) {
        replaceWithNegatedExpression(defaultValue);
      }
    }
  }

  @Override
  public void replaceWithNegatedExpression(PsiElement expression) {
    if (expression != null && PsiTreeUtil.getParentOfType(expression, PyImportStatementBase.class, false) == null) {
      final PyExpression replacement = invertExpression(expression);
      expression.replace(replacement);
    }
  }

  private static @NotNull PyExpression invertExpression(final @NotNull PsiElement expression) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
    Boolean booleanValue = PyEvaluator.getBooleanLiteralValue(expression);
    if (booleanValue != null) {
      final String value = booleanValue ? PyNames.FALSE : PyNames.TRUE;
      return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
    }
    else if (expression instanceof PyPrefixExpression) {
      if (((PyPrefixExpression)expression).getOperator() == PyTokenTypes.NOT_KEYWORD) {
        final PyExpression operand = ((PyPrefixExpression)expression).getOperand();
        if (operand != null)
          return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), operand.getText());
      }
    }
    return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), "not " + expression.getText());
  }

  private static boolean isBooleanLiteral(@Nullable PsiElement element) {
    return element != null && PyEvaluator.getBooleanLiteralValue(element) != null;
  }
}

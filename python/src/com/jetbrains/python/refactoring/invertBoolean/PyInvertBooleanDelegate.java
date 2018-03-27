/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PyInvertBooleanDelegate extends InvertBooleanDelegate {
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
    return element.getParent() instanceof PyBoolLiteralExpression;
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
        final String name = assignedValue.getText();
        return name != null && (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name));
      }
    }
    if (element instanceof PyNamedParameter) {
      final PyExpression defaultValue = ((PyNamedParameter)element).getDefaultValue();
      if (defaultValue instanceof PyBoolLiteralExpression) return true;
    }
    return element.getParent() instanceof PyBoolLiteralExpression;
  }

  @Nullable
  @Override
  public PsiElement adjustElement(PsiElement element, Project project, Editor editor) {
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
                                 Collection<PsiElement> elementsToInvert) {
    final Collection<PsiReference> refs = ReferencesSearch.search(psiElement).findAll();

    for (PsiReference ref : refs) {
      final PsiElement refElement = ref.getElement();
      if (!collectElementsToInvert(psiElement, refElement, elementsToInvert)) {
        collectForeignElementsToInvert(psiElement, refElement, PythonLanguage.getInstance(), elementsToInvert);
      }
    }
  }

  public PsiElement getElementToInvert(PsiElement namedElement, PsiElement element) {
    if (element instanceof PyTargetExpression) {
      final PyTargetExpression target = (PyTargetExpression)element;
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

  @NotNull
  private static PyExpression invertExpression(@NotNull final PsiElement expression) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
    if (expression instanceof PyBoolLiteralExpression) {
      final String value = ((PyBoolLiteralExpression)expression).getValue() ? PyNames.FALSE : PyNames.TRUE;
      return elementGenerator.createExpressionFromText(LanguageLevel.forElement(expression), value);
    }
    if (expression instanceof PyReferenceExpression && (PyNames.FALSE.equals(expression.getText()) ||
                                                        PyNames.TRUE.equals(expression.getText()))) {

      final String value = PyNames.TRUE.equals(expression.getText()) ? PyNames.FALSE : PyNames.TRUE;
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
}

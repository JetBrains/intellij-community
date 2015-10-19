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
package com.jetbrains.python.refactoring.move;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.makeFunctionTopLevel.PyMakeFunctionTopLevelDialog;
import com.jetbrains.python.refactoring.makeFunctionTopLevel.PyMakeLocalFunctionTopLevelProcessor;
import com.jetbrains.python.refactoring.makeFunctionTopLevel.PyMakeMethodTopLevelProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyMoveSymbolDelegate extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    if (!super.canMove(elements, targetContainer)) {
      return false;
    }
    // Local function or method
    if (findTargetFunction(elements[0]) != null) {
      return true;
    }
    
    // Top-level module member
    for (PsiElement element : elements) {
      if (!PyMoveModuleMembersHelper.isMovableModuleMember(element)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void doMove(Project project, PsiElement[] elements, @Nullable PsiElement targetContainer, @Nullable MoveCallback callback) {
    String initialPath = null;
    if (targetContainer instanceof PsiFile) {
      initialPath = StringUtil.notNullize(PyPsiUtils.getContainingFilePath(targetContainer));
    }
    if (initialPath == null) {
      initialPath = StringUtil.notNullize(PyPsiUtils.getContainingFilePath(elements[0]));
    }
    
    final BaseRefactoringProcessor processor;
    final PyFunction function = findTargetFunction(elements[0]);
    if (function != null) {
      final PyMakeFunctionTopLevelDialog dialog = new PyMakeFunctionTopLevelDialog(project, function, initialPath, initialPath);
      if (!dialog.showAndGet()) {
        return;
      }
      if (function.getContainingClass() != null) {
        processor = new PyMakeMethodTopLevelProcessor(function, dialog.getTargetPath());
      }
      else {
        processor = new PyMakeLocalFunctionTopLevelProcessor(function, dialog.getTargetPath());
      }
      processor.setPreviewUsages(dialog.isPreviewUsages());
    }
    else {
      final List<PsiNamedElement> initialElements = Lists.newArrayList();
      for (PsiElement element : elements) {
        final PsiNamedElement e = PyMoveModuleMembersHelper.extractNamedElement(element);
        if (e == null) {
          return;
        }
        initialElements.add(e);
      }
      final PyMoveModuleMembersDialog dialog = new PyMoveModuleMembersDialog(project, initialElements, initialPath, initialPath);
      if (!dialog.showAndGet()) {
        return;
      }
      final boolean previewUsages = dialog.isPreviewUsages();
      final PsiNamedElement[] selectedElements = ContainerUtil.findAllAsArray(dialog.getSelectedTopLevelSymbols(), PsiNamedElement.class);
      processor = new PyMoveModuleMembersProcessor(project, selectedElements, dialog.getTargetPath(), previewUsages);
    }
    
    try {
      processor.run();
    }
    catch (IncorrectOperationException e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw e;
      }
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), null, project);
    }
  }

  @Override
  public boolean tryToMove(@NotNull PsiElement element,
                           @NotNull Project project,
                           @Nullable DataContext dataContext,
                           @Nullable PsiReference reference,
                           @Nullable Editor editor) {
    PsiFile targetContainer = null;
    if (editor != null) {
      final Document document = editor.getDocument();
      targetContainer = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (targetContainer instanceof PyFile && selectionSpansMultipleLines(editor)) {
        final List<PyElement> moduleMembers = collectAllMovableElementsInSelection(editor, (PyFile)targetContainer);
        if (moduleMembers.isEmpty()) {
          showBadSelectionErrorHint(project, editor);
        }
        else {
          doMove(project, ContainerUtil.findAllAsArray(moduleMembers, PsiNamedElement.class), targetContainer, null);
        }
        return true;
      }
    }

    // Fallback to the old way to select single element to move
    final PsiNamedElement e = PyMoveModuleMembersHelper.extractNamedElement(element);
    if (e != null && PyMoveModuleMembersHelper.hasMovableElementType(e)) {
      if (PyMoveModuleMembersHelper.isMovableModuleMember(e) || findTargetFunction(e) != null) {
        doMove(project, new PsiElement[]{e}, targetContainer, null);
      }
      else {
        showBadSelectionErrorHint(project, editor);
      }
      return true;
    }
    return false;
  }

  private static void showBadSelectionErrorHint(@NotNull Project project, @Nullable Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor,
                                        PyBundle.message("refactoring.move.module.members.error.selection"),
                                        RefactoringBundle.message("error.title"), null);
  }

  private static boolean selectionSpansMultipleLines(@NotNull Editor editor) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final Document document = editor.getDocument();
    return document.getLineNumber(selectionModel.getSelectionStart()) != document.getLineNumber(selectionModel.getSelectionEnd());
  }

  @NotNull
  private static List<PyElement> collectAllMovableElementsInSelection(@NotNull Editor editor, @NotNull PyFile pyFile) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final TextRange selectionRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    final List<PyElement> members = PyMoveModuleMembersHelper.getTopLevelModuleMembers(pyFile);
    return ContainerUtil.filter(members, member -> {
      final PsiElement body = PyMoveModuleMembersHelper.expandNamedElementBody(((PsiNamedElement)member));
      return body != null && selectionRange.contains(body.getTextRange());
    });
  }

  @Nullable
  public static PyFunction findTargetFunction(@NotNull PsiElement element) {
    if (isLocalFunction(element) || isSuitableInstanceMethod(element)) {
      return (PyFunction)element;
    }
    // e.g. caret is on "def" keyword
    if (isLocalFunction(element.getParent()) || isSuitableInstanceMethod(element.getParent())) {
      return (PyFunction)element.getParent();
    }
    final PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(element, PyReferenceExpression.class);
    if (refExpr == null) {
      return null;
    }
    final PsiElement resolved = refExpr.getReference().resolve();
    if (isLocalFunction(resolved) || isSuitableInstanceMethod(resolved)) {
      return (PyFunction)resolved;
    }
    return null;
  }

  public static boolean isSuitableInstanceMethod(@Nullable PsiElement element) {
    final PyFunction function = as(element, PyFunction.class);
    if (function == null || function.getContainingClass() == null) {
      return false;
    }
    final String funcName = function.getName();
    if (funcName == null || PyUtil.isSpecialName(funcName)) {
      return false;
    }
    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(function.getProject(), function.getContainingFile());
    if (PySuperMethodsSearch.search(function, typeEvalContext).findFirst() != null) return false;
    if (PyOverridingMethodsSearch.search(function, true).findFirst() != null) return false;
    if (function.getDecoratorList() != null || function.getModifier() != null) return false;
    if (function.getContainingClass().findPropertyByCallable(function) != null) return false;
    return true;
  }

  private static boolean isLocalFunction(@Nullable PsiElement resolved) {
    return resolved instanceof PyFunction && PsiTreeUtil.getParentOfType(resolved, ScopeOwner.class, true) instanceof PyFunction;
  }
}

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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.move.makeFunctionTopLevel.PyMakeFunctionTopLevelDialog;
import com.jetbrains.python.refactoring.move.makeFunctionTopLevel.PyMakeLocalFunctionTopLevelProcessor;
import com.jetbrains.python.refactoring.move.makeFunctionTopLevel.PyMakeMethodTopLevelProcessor;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersDialog;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersHelper;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author vlan
 */
public class PyMoveSymbolDelegate extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    if (targetContainer != null && !super.canMove(elements, targetContainer)) {
      return false;
    }
    // Local function or method
    if (isMovableLocalFunctionOrMethod(elements[0])) {
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

  public void doMove(@NotNull Project project, @NotNull List<PyElement> elements) {
    final PsiElement firstElement = elements.get(0);
    final String initialPath = StringUtil.notNullize(PyPsiUtils.getContainingFilePath(firstElement));

    final BaseRefactoringProcessor processor;
    if (isMovableLocalFunctionOrMethod(firstElement)) {
      final PyFunction function = (PyFunction)firstElement;
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
      final PsiNamedElement[] selectedElements = ContainerUtil.findAllAsArray(dialog.getSelectedTopLevelSymbols(), PsiNamedElement.class);
      processor = new PyMoveModuleMembersProcessor(selectedElements, dialog.getTargetPath());
      processor.setPreviewUsages(dialog.isPreviewUsages());
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
    final PsiFile currentFile = element.getContainingFile();
    if (editor != null && currentFile instanceof PyFile && selectionSpansMultipleLines(editor)) {
      final List<PyElement> moduleMembers = collectAllMovableElementsInSelection(editor, (PyFile)currentFile);
      if (moduleMembers.isEmpty()) {
        showBadSelectionErrorHint(project, editor);
      }
      else {
        doMove(project, moduleMembers);
      }
      return true;
    }

    // Fallback to the old way to select single element to move
    final PsiNamedElement e = PyMoveModuleMembersHelper.extractNamedElement(element);
    if (e != null && PyMoveModuleMembersHelper.hasMovableElementType(e)) {
      if (PyMoveModuleMembersHelper.isMovableModuleMember(e) || isMovableLocalFunctionOrMethod(e)) {
        doMove(project, Collections.singletonList((PyElement)e));
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
      final PsiElement body = PyMoveModuleMembersHelper.expandNamedElementBody((PsiNamedElement)member);
      return body != null && selectionRange.contains(body.getTextRange());
    });
  }

  @VisibleForTesting
  public static boolean isMovableLocalFunctionOrMethod(@NotNull PsiElement element) {
    return isLocalFunction(element) || isSuitableInstanceMethod(element);
  }

  private static boolean isSuitableInstanceMethod(@Nullable PsiElement element) {
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

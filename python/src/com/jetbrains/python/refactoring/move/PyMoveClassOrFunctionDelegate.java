/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionDelegate extends MoveHandlerDelegate {

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    for (PsiElement element : elements) {
      if (element instanceof PyClass || element instanceof PyFunction) continue;
      return false;
    }
    return super.canMove(elements, targetContainer);
  }

  @Override
  public void doMove(Project project,
                     PsiElement[] elements,
                     @Nullable PsiElement targetContainer,
                     @Nullable MoveCallback callback) {
    PsiNamedElement[] elementsToMove = new PsiNamedElement[elements.length];
    for (int i = 0; i < elements.length; i++) {
      final PsiNamedElement e = getElementToMove(elements[i]);
      if (e == null) {
        return;
      }
      elementsToMove[i] = e;
    }
    String initialDestination = null;
    if (targetContainer instanceof PsiFile) {
      final VirtualFile virtualFile = ((PsiFile)targetContainer).getVirtualFile();
      if (virtualFile != null) {
        initialDestination = FileUtil.toSystemDependentName(virtualFile.getPath());
      }
    }
    final PyMoveClassOrFunctionDialog dialog = new PyMoveClassOrFunctionDialog(project, elementsToMove, initialDestination);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final String destination = dialog.getTargetPath();
    final boolean previewUsages = dialog.isPreviewUsages();
    try {
      final BaseRefactoringProcessor processor = new PyMoveClassOrFunctionProcessor(project, elementsToMove, destination, previewUsages);
      processor.run();
    }
    catch (IncorrectOperationException e) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(),
                                             null, project);
    }
  }

  @Override
  public boolean tryToMove(@NotNull PsiElement element,
                           @NotNull Project project,
                           @Nullable DataContext dataContext,
                           @Nullable PsiReference reference,
                           @Nullable Editor editor) {
    final PsiNamedElement e = getElementToMove(element);
    if (e instanceof PyClass || e instanceof PyFunction) {
      if (PyUtil.isTopLevel(e)) {
        PsiElement targetContainer = null;
        if (editor != null) {
          final Document document = editor.getDocument();
          targetContainer = PsiDocumentManager.getInstance(project).getPsiFile(document);
        }
        doMove(project, new PsiElement[] {e}, targetContainer, null);
      }
      else {
        CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.move.class.or.function.error.selection"),
                                            RefactoringBundle.message("error.title"), null);
      }
      return true;
    }
    return false;
  }

  @Nullable
  public static PsiNamedElement getElementToMove(@NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return (PsiNamedElement)element;
    }
    return null;
  }
}

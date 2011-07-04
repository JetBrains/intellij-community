package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyMoveClassOrFunctionDelegate extends MoveHandlerDelegate {

  @Override
  public void doMove(Project project,
                     PsiElement[] elements,
                     @Nullable PsiElement targetContainer,
                     @Nullable MoveCallback callback) {
    PsiNamedElement[] elementsToMove = new PsiNamedElement[elements.length];
    for (int i = 0; i < elements.length; i++) {
      PsiNamedElement e = getElementToMove(elements[i]);
      if (e == null) {
        return;
      }
      elementsToMove[i] = e;
    }
    boolean previewUsages = false;
    final String destination;
    if (targetContainer instanceof PyFile) {
      destination = targetContainer.getText();
    }
    else {
      final PyMoveClassOrFunctionDialog dialog = new PyMoveClassOrFunctionDialog(project, elementsToMove);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      destination = dialog.getTargetPath();
    }
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
    final PsiNamedElement elementToMove = getElementToMove(element);
    if (elementToMove != null) {
      doMove(project, new PsiElement[] {element}, null, null);
      return true;
    }
    if (element instanceof PsiNamedElement && !(element instanceof PyParameter)) {
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiNamedElement getElementToMove(@NotNull PsiElement element) {
    if (element instanceof PyFunction && ((PyFunction)element).isTopLevel() ||
        element instanceof PyClass && ((PyClass)element).isTopLevel()) {
      return (PsiNamedElement)element;
    }
    return null;
  }
}

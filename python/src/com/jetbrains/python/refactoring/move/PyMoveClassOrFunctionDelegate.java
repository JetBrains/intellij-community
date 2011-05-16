package com.jetbrains.python.refactoring.move;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
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

    final PsiFile targetFile;
    boolean previewUsages = false;

    if (targetContainer instanceof PyFile) {
      targetFile = (PsiFile)targetContainer;
    } else {
      final PyMoveClassOrFunctionDialog dialog = new PyMoveClassOrFunctionDialog(project, elementsToMove);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      targetFile = dialog.getTargetFile();
      previewUsages = dialog.isPreviewUsages();
    }

    CommonRefactoringUtil.checkReadOnlyStatus(project, targetFile);
    for (PsiNamedElement e: elementsToMove) {
      CommonRefactoringUtil.checkReadOnlyStatus(project, e);
    }

    try {
      if (!(targetFile instanceof PyFile)) {
        throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.place.elements.into.nonpython.file"));
      }
      PyFile file = (PyFile)targetFile;
      for (PsiNamedElement e: elementsToMove) {
        assert e instanceof PyClass || e instanceof PyFunction;
        if (e instanceof PyClass && file.findTopLevelClass(e.getName()) != null) {
          throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.class.$0", e.getName()));
        }
        if (e instanceof PyFunction && file.findTopLevelFunction(e.getName()) != null) {
          throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.destination.file.contains.function.$0", e.getName()));
        }
        checkValidImportableFile(file, e.getContainingFile().getVirtualFile());
        checkValidImportableFile(e, file.getVirtualFile());
      }
      // TODO: Check for resulting circular imports
      final BaseRefactoringProcessor processor = new PyMoveClassOrFunctionProcessor(project, elementsToMove, (PyFile)targetFile,
                                                                                    previewUsages);
      processor.run();
    }
    catch (IncorrectOperationException e) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(),
                                             null, project);
    }
  }

  private static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
    final PyQualifiedName qName = ResolveImportUtil.findShortestImportableQName(anchor, file);
    if (!PyClassRefactoringUtil.isValidQualifiedName(qName)) {
      throw new IncorrectOperationException(PyBundle.message("refactoring.move.class.or.function.error.cannot.use.module.name.$0", qName));
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
    return false;
  }

  @Nullable
  private static PsiNamedElement getElementToMove(@NotNull PsiElement element) {
    final PsiNamedElement result = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false, PyClass.class, PyFunction.class);
    if (result instanceof PyFunction && ((PyFunction)result).isTopLevel() ||
        result instanceof PyClass && ((PyClass)result).isTopLevel()) {
      return result;
    }
    return null;
  }
}

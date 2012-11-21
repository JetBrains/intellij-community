package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */

public class PyChangeSignatureHandler implements ChangeSignatureHandler {
  @Nullable
  @Override
  public PsiElement findTargetMember(PsiFile file, Editor editor) {
    PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    return findTargetMember(element);
  }

  @Nullable
  @Override
  public PsiElement findTargetMember(PsiElement element) {
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    return function;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    invokeOnElement(project, element);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    if (elements.length != 1) return;
    invokeOnElement(project, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return PyBundle.message("refactoring.change.signature.error.wrong.caret.position.method.name");
  }

  private static void invokeOnElement(Project project, PsiElement element) {
    final PyFunction newFunction = getSuperMethod((PyFunction)element);
    if (newFunction == null) return;
    if (!newFunction.equals(element)) {
      invokeOnElement(project, newFunction);
    }
    else {
      final PyMethodDescriptor method = new PyMethodDescriptor((PyFunction)element);
      PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, method);
      dialog.show();
    }
  }

  @Nullable
  protected static PyFunction getSuperMethod(@Nullable PyFunction function) {
    if (function == null) return null;
    final PyClass clazz = function.getContainingClass();
    if (clazz == null) {
      return function;
    }
    final PsiElement result = PySuperMethodsSearch.search(function).findFirst();
    if (result != null) {
      final PyClass baseClass = ((PyFunction)result).getContainingClass();
      String baseClassName = baseClass == null? "" : baseClass.getName();
      final String message = PyBundle.message("refactoring.change.signature.find.usages.of.base.class",
                                              function.getName(),
                                              clazz.getName(),
                                              baseClassName);
      int choice;
      if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) {
        choice = 0;
      }
      else {
        choice = Messages.showYesNoCancelDialog(function.getProject(), message, REFACTORING_NAME, Messages.getQuestionIcon());
      }
      switch (choice) {
        case 0:
          return (PyFunction)result;
        case 1:
          return function;
        default:
          return null;
      }

    }
    return function;
  }
}


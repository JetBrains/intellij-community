package com.intellij.refactoring.changeSignature;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.refactoring.util.RefactoringMessageUtil;

public class ChangeSignatureHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Change Signature";

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = (PsiElement) dataContext.getData(DataConstants.PSI_ELEMENT);
    if (element instanceof PsiMethod) {
      PsiElement resolutionContext = file.findElementAt(editor.getCaretModel().getOffset());
      if(resolutionContext == null) resolutionContext = element;
      resolutionContext = ChangeSignatureProcessor.normalizeResolutionContext(resolutionContext);
      invoke((PsiMethod) element, project, resolutionContext);
    }
    else if (element instanceof PsiClass) {
      invoke((PsiClass) element);
    }
    else {
      String message = "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of the method to be refactored.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.CHANGE_SIGNATURE, project);
    }
  }

  public void invoke(final Project project, final PsiElement[] elements, final DataContext dataContext) {
    if (elements.length != 1) return;
    if (elements[0] instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)elements[0];
      final PsiMethod resolutionContext = method;

      invoke(method, project, resolutionContext);
    }
    else if (elements[0] instanceof PsiClass){
      invoke((PsiClass) elements[0]);
    }
  }

  private void invoke(final PsiMethod method, final Project project, final PsiElement resolutionContext) {
    if (!method.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, method)) return;
    }

    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, "refactor");
    if (newMethod == null) return;

    newMethod = (PsiMethod) EjbUtil.checkDeclMethod(newMethod, "refactor");
    if (newMethod == null) return;

    if (!newMethod.equals(method)) {
      final PsiMethod methodCopy = newMethod;
      invoke(methodCopy, project, resolutionContext);
      return;
    }

    if (!method.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, method)) return;
    }

    final PsiClass containingClass = method.getContainingClass();
    final ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, containingClass != null && !containingClass.isInterface());
    dialog.show();
  }

  private void invoke(final PsiClass aClass) {
    Project project = aClass.getProject();
    if (!aClass.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, aClass)) return;
    }

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(aClass);
    dialog.show();
  }
}
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryHandler
        implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Replace Constructor With Factory Method";
  private Project myProject;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message =
                "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside the constructor to be refactored.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY*/, project);
        return;
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)
              && ((PsiClass) element).getConstructors().length == 0) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    if (elements[0] instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) elements[0];
      invoke(method);
    } else if (elements[0] instanceof PsiClass) {
      invoke((PsiClass) elements[0]);
    }

  }

  private void invoke(PsiClass aClass) {
    String qualifiedName = aClass.getQualifiedName();
    if(qualifiedName == null) {
      showJspOrLocalClassMessage();
      return;
    }
    if (!checkAbstractClassOrInterfaceMessage(aClass)) return;
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length > 0) {
      String message =
              "Class " + aClass.getQualifiedName() + " does not have implicit default consructor." ;
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY*/, myProject);
      return;
    }
    final int answer = Messages.showYesNoCancelDialog(myProject,
            "Would you like to replace default constructor of " + aClass.getQualifiedName() + " with factory method?",
            REFACTORING_NAME, Messages.getQuestionIcon()
    );
    if (answer != 0) return;
    if (!aClass.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(myProject, aClass)) return;
    }
    new ReplaceConstructorWithFactoryDialog(myProject, null, aClass).show();
  }

  private void showJspOrLocalClassMessage() {
    String message =
            "Cannot perform the refactoring.\n" +
            "Refactoring is not supported for local and JSP classes.";
    RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY*/, myProject);
  }
  private boolean checkAbstractClassOrInterfaceMessage(PsiClass aClass) {
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    final String reason = aClass.isInterface() ? "an interface" : "abstract";
    String message =
            "Cannot perform the refactoring.\n" +
            aClass.getQualifiedName() + " is " + reason + ".";
    RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, myProject);
    return false;
  }

  private void invoke(final PsiMethod method) {
    if (!method.isConstructor()) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Method is not a constructor.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, myProject);
      return;
    }

    PsiClass aClass = method.getContainingClass();
    if(aClass == null || aClass.getQualifiedName() == null) {
      showJspOrLocalClassMessage();
      return;
    }

    if (!checkAbstractClassOrInterfaceMessage(aClass)) return;

    if (!method.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(myProject, method)) return;
    }
    new ReplaceConstructorWithFactoryDialog(myProject, method, method.getContainingClass()).show();
  }
}

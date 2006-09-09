package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryHandler
        implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.constructor.with.factory.method.title");
  private Project myProject;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.constructor"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, project);
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
              RefactoringBundle.message("class.does.not.have.implicit.default.consructor", aClass.getQualifiedName()) ;
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY*/, myProject);
      return;
    }
    final int answer = Messages.showYesNoCancelDialog(myProject,
                                                      RefactoringBundle.message("would.you.like.to.replace.default.constructor.of.0.with.factory.method", aClass.getQualifiedName()),
                                                      REFACTORING_NAME, Messages.getQuestionIcon()
    );
    if (answer != 0) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, aClass)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, null, aClass).show();
  }

  private void showJspOrLocalClassMessage() {
    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("refactoring.is.not.supported.for.local.and.jsp.classes"));
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, myProject);
  }
  private boolean checkAbstractClassOrInterfaceMessage(PsiClass aClass) {
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    String message = RefactoringBundle.getCannotRefactorMessage(aClass.isInterface() ?
                                                                RefactoringBundle.message("class.is.interface", aClass.getQualifiedName()) :
                                                                RefactoringBundle.message("class.is.abstract", aClass.getQualifiedName()));
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, myProject);
    return false;
  }

  private void invoke(final PsiMethod method) {
    if (!method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.is.not.a.constructor"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, myProject);
      return;
    }

    PsiClass aClass = method.getContainingClass();
    if(aClass == null || aClass.getQualifiedName() == null) {
      showJspOrLocalClassMessage();
      return;
    }

    if (!checkAbstractClassOrInterfaceMessage(aClass)) return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, method)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, method, method.getContainingClass()).show();
  }
}

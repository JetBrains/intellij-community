/*
* Created by IntelliJ IDEA.
* User: dsl
* Date: Apr 15, 2002
* Time: 1:25:37 PM
* To change template for new class use
* Code Style | Class Templates options (Tools | IDE Options).
*/
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeStaticHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("make.method.static.title");
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticHandler");

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiTypeParameterListOwner)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.method.or.class.name"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, project);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if(elements.length != 1 || !(elements[0] instanceof PsiTypeParameterListOwner)) return;

    final PsiTypeParameterListOwner member = (PsiTypeParameterListOwner)elements[0];
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, member)) return;

    String error = validateTarget(member);
    if (error != null) {
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, error, HelpID.MAKE_METHOD_STATIC, project);
      return;
    }

    final InternalUsageInfo[] classRefsInMember = MakeStaticUtil.findClassRefsInMember(member, false);

    /*
    String classParameterName = "anObject";
    ParameterTablePanel.VariableData[] fieldParameterData = null;

    */
    AbstractMakeStaticDialog dialog;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {

      if (classRefsInMember.length > 0) {
        final PsiType type =
                member.getManager().getElementFactory().createType(member.getContainingClass());
        //TODO: callback
        String[] nameSuggestions =
                CodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, null, null, type).names;

        dialog = new MakeParameterizedStaticDialog(project, member,
                                                   nameSuggestions,
                                                   classRefsInMember);


      }
      else {
        dialog = new SimpleMakeStaticDialog(project, member);
      }

      dialog.show();
    }
  }

  @Nullable
  public static String validateTarget(final PsiTypeParameterListOwner member) {
    final PsiClass containingClass = member.getContainingClass();

    // Checking various preconditions
    if(member instanceof PsiMethod && ((PsiMethod)member).isConstructor()) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("constructor.cannot.be.made.static"));
    }

    if(member.getContainingClass() == null) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("this.member.does.not.seem.to.belong.to.any.class"));
    }

    if(member.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("member.is.already.static"));
    }

    if(member instanceof PsiMethod && member.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.make.abstract.method.static"));
    }

    if(containingClass.getContainingClass() != null
       && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("inner.classes.cannot.have.static.members"));
    }
    return null;
  }
}

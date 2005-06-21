/*
* Created by IntelliJ IDEA.
* User: dsl
* Date: Apr 15, 2002
* Time: 1:25:37 PM
* To change template for new class use
* Code Style | Class Templates options (Tools | IDE Options).
*/
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
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
/*import com.intellij.refactoring.util.ParameterTablePanel;*/
import com.intellij.refactoring.util.RefactoringMessageUtil;

public class MakeStaticHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Make Method Static";
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticHandler");
  private Project myProject;
  private PsiTypeParameterListOwner myMember;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiTypeParameterListOwner)) {
      String message = "Cannot perform the refactoring.\n" +
              "The caret should be positioned at the name of the method or class to be refactored.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, project);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    if(elements.length != 1 || !(elements[0] instanceof PsiTypeParameterListOwner)) return;

    myProject = project;
    myMember = (PsiTypeParameterListOwner)elements[0];
    if (!myMember.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, myMember)) return;
    }

    final PsiClass containingClass;


    // Checking various preconditions
    if(myMember instanceof PsiMethod && ((PsiMethod)myMember).isConstructor()) {
      String message = "Cannot perform the refactoring.\n" +
              "Constructor cannot be made static.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, myProject);
      return;
    }

    if(myMember.getContainingClass() == null) {
      String message = "Cannot perform the refactoring.\n" +
              "This member does not seem to belong to any class.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, myProject);
      return;
    }
    containingClass = myMember.getContainingClass();

    if(myMember.hasModifierProperty(PsiModifier.STATIC)) {
      String message = "Cannot perform the refactoring.\n" +
              "Member is already static.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, myProject);
      return;
    }

    if(myMember.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String message = "Cannot perfrom the refactoring.\n" +
              "Cannot make abstract method static.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, myProject);
      return;
    }

    if(containingClass.getContainingClass() != null
            && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      String message = "Cannot perform the refactoring.\n" +
              "Inner classes cannot have static members.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MAKE_METHOD_STATIC, myProject);
      return;
    }

    final InternalUsageInfo[] classRefsInMember = MakeStaticUtil.findClassRefsInMember(myMember, false);

    /*
    String classParameterName = "anObject";
    ParameterTablePanel.VariableData[] fieldParameterData = null;

    */
    AbstractMakeStaticDialog dialog;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {

      if (classRefsInMember.length > 0) {
        final PsiType type =
                containingClass.getManager().getElementFactory().createType(containingClass);
        //TODO: callback
        String[] nameSuggestions =
                CodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, null, null, type).names;

        dialog = new MakeParameterizedStaticDialog(myProject, myMember,
                                                   nameSuggestions,
                                                   classRefsInMember);


      }
      else {
        dialog = new SimpleMakeStaticDialog(myProject, myMember);
      }

      dialog.show();
      return;
    }
  }
}

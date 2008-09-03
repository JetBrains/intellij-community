package com.intellij.refactoring.introduceField;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.occurences.*;
import org.jetbrains.annotations.NotNull;

public class IntroduceFieldHandler extends BaseExpressionToFieldHandler {

  public static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");
  private static final MyOccurenceFilter MY_OCCURENCE_FILTER = new MyOccurenceFilter();

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  protected boolean validClass(PsiClass parentClass) {
    if (parentClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.field.in.interface"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, getHelpID(),
                                              parentClass.getProject());
      return false;
    }
    else {
      return true;
    }
  }

  protected String getHelpID() {
    return HelpID.INTRODUCE_FIELD;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ElementToWorkOn elementToWorkOn = ElementToWorkOn.getElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_FIELD, project);

    if (elementToWorkOn == null) return;

    if (elementToWorkOn.getExpression() == null) {
      final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
      final boolean result = invokeImpl(project, localVariable, editor);
      if (result) {
        editor.getSelectionModel().removeSelection();
      }
    }
    else if (invokeImpl(project, elementToWorkOn.getExpression(), editor)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  protected Settings showRefactoringDialog(Project project, PsiClass parentClass, PsiExpression expr,
                                           PsiType type,
                                           PsiExpression[] occurences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
    final PsiModifierListOwner staticParentElement = PsiUtil.getEnclosingStaticElement(expr, parentClass);
    boolean declareStatic = staticParentElement != null;

    boolean isInSuperOrThis = false;
    if (!declareStatic) {
      for (int i = 0; !declareStatic && i < occurences.length; i++) {
        PsiExpression occurence = occurences[i];
        isInSuperOrThis = isInSuperOrThis(occurence);
        declareStatic = isInSuperOrThis;
      }
    }

    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression)expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable)ref;
      }
    }

    int occurencesNumber = occurences.length;
    final boolean currentMethodConstructor = containingMethod != null && containingMethod.isConstructor();
    final boolean allowInitInMethod = (!currentMethodConstructor || !isInSuperOrThis) && anchorElement instanceof PsiStatement;
    final boolean allowInitInMethodIfAll = (!currentMethodConstructor || !isInSuperOrThis) && anchorElementIfAll instanceof PsiStatement;
    IntroduceFieldDialog dialog = new IntroduceFieldDialog(
      project, parentClass, expr, localVariable,
      currentMethodConstructor,
      false, declareStatic, occurencesNumber,
      allowInitInMethod, allowInitInMethodIfAll,
      new TypeSelectorManagerImpl(project, type, expr, occurences)
    );
    dialog.show();

    if (!dialog.isOK()) {
      if (occurencesNumber > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
      return null;
    }

    if (!dialog.isDeleteVariable()) {
      localVariable = null;
    }


    return new Settings(dialog.getEnteredName(), dialog.isReplaceAllOccurrences(),
                        declareStatic, dialog.isDeclareFinal(),
                        dialog.getInitializerPlace(), dialog.getFieldVisibility(),
                        localVariable,
                        dialog.getFieldType(), localVariable != null, null, false, false);
  }

  private static boolean isInSuperOrThis(PsiExpression occurence) {
    return !NotInSuperCallOccurenceFilter.INSTANCE.isOK(occurence) || !NotInThisCallFilter.INSTANCE.isOK(occurence);
  }

  protected OccurenceManager createOccurenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    final OccurenceFilter occurenceFilter = isInSuperOrThis(selectedExpr) ? null : MY_OCCURENCE_FILTER;
    return new ExpressionOccurenceManager(selectedExpr, parentClass, occurenceFilter, true);
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, false);
    return localToFieldHandler.convertLocalToField(localVariable, editor);
  }

  private static class MyOccurenceFilter implements OccurenceFilter {
    public boolean isOK(PsiExpression occurence) {
      return !isInSuperOrThis(occurence);
    }
  }
}
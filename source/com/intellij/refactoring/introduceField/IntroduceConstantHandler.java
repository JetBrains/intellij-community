package com.intellij.refactoring.introduceField;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceManager;

public class IntroduceConstantHandler extends BaseExpressionToFieldHandler {
  public static final String REFACTORING_NAME = "Introduce Constant";

  protected String getHelpID() {
    return /*HelpID.INTRODUCE_CONSTANT*/ null;
  }

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    if (!file.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, file);
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final ElementToWorkOn elementToWorkOn = ElementToWorkOn.getElementToWorkOn(editor, file,
            REFACTORING_NAME, getHelpID(), project
    );

    if (elementToWorkOn == null) return;

    if (elementToWorkOn.getExpression() == null) {
      final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
      final boolean result = invokeImpl(project, localVariable, editor);
      if (result) {
        editor.getSelectionModel().removeSelection();
      }
    } else if (invokeImpl(project, elementToWorkOn.getExpression(), editor)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  protected boolean invokeImpl(Project project, final PsiLocalVariable localVariable, Editor editor) {
    final LocalToFieldHandler localToFieldHandler = new LocalToFieldHandler(project, true);
    final boolean result = localToFieldHandler.convertLocalToField(localVariable, editor);
    return result;
  }


  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(Project project, PsiClass parentClass,
                                                                        PsiExpression expr,
                                                                        PsiType type, PsiExpression[] occurences,
                                                                        PsiElement anchorElement,
                                                                        PsiElement anchorElementIfAll) {
    PsiLocalVariable localVariable = null;
    if (expr instanceof PsiReferenceExpression) {
      PsiElement ref = ((PsiReferenceExpression) expr).resolve();
      if (ref instanceof PsiLocalVariable) {
        localVariable = (PsiLocalVariable) ref;
      }
    }

    if (localVariable == null) {
      if (!isStaticFinalInitializer(expr)) {
        String message =
                "Cannot perform the refactoring.\n" +
                "Selected expression cannot be a constant initializer.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, getHelpID(), project);
        return null;
      }
    } else {
      final PsiExpression initializer = localVariable.getInitializer();
      if (initializer == null) {
        String message =
                "Cannot perform the refactoring.\n" +
                "Variable " + localVariable.getName() + " does not have an initializer.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, getHelpID(), project);
        return null;
      }
      if (!isStaticFinalInitializer(initializer)) {
        String message =
                "Cannot perform the refactoring.\n" +
                "Initializer for variable " + localVariable.getName() + " cannot be a constant initializer.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, getHelpID(), project);
        return null;
      }
    }

    int occurencesNumber = occurences.length;
    IntroduceConstantDialog dialog = new IntroduceConstantDialog(
            project, parentClass, expr, localVariable, false, occurencesNumber,
            getParentClass(), new TypeSelectorManagerImpl(project, type, expr, occurences)
    );
    dialog.show();
    if (!dialog.isOK()) {
      if (occurencesNumber > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo("Press Escape to remove the highlighting");
      }
      return null;
    }
    return new Settings(dialog.getEnteredName(), dialog.isReplaceAllOccurrences(),
            true, true, BaseExpressionToFieldHandler.IN_FIELD_DECLARATION,
            dialog.getFieldVisibility(),
            localVariable,
            dialog.getSelectedType(), dialog.isDeleteVariable(),
            dialog.getDestinationClass());
  }


  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  private boolean isStaticFinalInitializer(PsiExpression expr) {
    PsiClass parentClass = getParentClass(expr);
    if (parentClass == null) return true;
    IsStaticFinalInitializerExpression visitor = new IsStaticFinalInitializerExpression(parentClass, expr);
    expr.accept(visitor);
    return visitor.isStaticFinalInitializer();
  }

  protected OccurenceManager createOccurenceManager(final PsiExpression selectedExpr, final PsiClass parentClass) {
    OccurenceManager occurenceManager = new ExpressionOccurenceManager(selectedExpr, parentClass, null);
    return occurenceManager;
  }

  private static class IsStaticFinalInitializerExpression extends ClassMemberReferencesVisitor {
    private boolean myIsStaticFinalInitializer = true;
    private final PsiExpression myInitializer;

    public IsStaticFinalInitializerExpression(PsiClass aClass, PsiExpression initializer) {
      super(aClass);
      myInitializer = initializer;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement psiElement = expression.resolve();
      if ((psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter)
          && !PsiTreeUtil.isAncestor(myInitializer, psiElement, false)) {
        myIsStaticFinalInitializer = false;
      } else {
        super.visitReferenceExpression(expression);
      }
    }


    protected void visitClassMemberReferenceElement(PsiMember classMember,
                                                    PsiJavaCodeReferenceElement classMemberReference) {
     myIsStaticFinalInitializer = classMember.hasModifierProperty(PsiModifier.STATIC);
    }

    public void visitElement(PsiElement element) {
      if (!myIsStaticFinalInitializer) return;
      super.visitElement(element);
    }

    public boolean isStaticFinalInitializer() {
      return myIsStaticFinalInitializer;
    }
  }

  public PsiClass getParentClass(PsiExpression initializerExpression) {
    final PsiType type = initializerExpression.getType();

    if (type != null && PsiUtil.isConstantExpression(initializerExpression)) {
      if (type instanceof PsiPrimitiveType ||
          PsiType.getJavaLangString(initializerExpression.getManager()).equals(type)) {
        return super.getParentClass(initializerExpression);
      }
    }

    PsiClass aClass = PsiTreeUtil.getParentOfType(initializerExpression, PsiClass.class);
    while (aClass != null) {
      if (aClass.hasModifierProperty(PsiModifier.STATIC)) return aClass;
      if (aClass.getParent() instanceof PsiJavaFile) return aClass;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }
    return null;
  }

  protected boolean validClass(PsiClass parentClass) {
    return true;
  }

  protected boolean isStaticField() {
    return true;
  }
}

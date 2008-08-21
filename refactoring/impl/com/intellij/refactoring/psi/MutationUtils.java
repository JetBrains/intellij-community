package com.intellij.refactoring.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public class MutationUtils {
    private MutationUtils() {
        super();
    }


  public static void replaceType(String newExpression,
                                   PsiTypeElement typeElement)
            throws IncorrectOperationException {
        final PsiManager mgr = typeElement.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiType newType =
                factory.createTypeFromText(newExpression, null);
        final PsiTypeElement newTypeElement = factory.createTypeElement(newType);
        final PsiElement insertedElement = typeElement.replace(newTypeElement);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    public static void replaceExpression(String newExpression,
                                         PsiExpression exp)
            throws IncorrectOperationException {
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, null);
        final PsiElement insertedElement = exp.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    public static void replaceExpressionIfValid(String newExpression,
                                         PsiExpression exp) throws IncorrectOperationException{
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiExpression newCall;
        try{
            newCall = factory.createExpressionFromText(newExpression, null);
        } catch(IncorrectOperationException e){
            return;
        }
        final PsiElement insertedElement = exp.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiElement shortenedElement =JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

    public static void replaceReference(String className,
                                        PsiJavaCodeReferenceElement reference)
            throws IncorrectOperationException {
            final PsiManager mgr = reference.getManager();
            final Project project = mgr.getProject();
            final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

            final PsiJavaCodeReferenceElement newReference =
                    factory.createReferenceElementByFQClassName(className, scope);
            final PsiElement insertedElement = reference.replace(newReference);
            final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
            final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
            codeStyleManager.reformat(shortenedElement);   
    }

    public static void replaceStatement(String newStatement,
                                        PsiStatement statement)
            throws IncorrectOperationException {
        final Project project = statement.getProject();
        final PsiManager mgr = PsiManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getInstance(mgr.getProject()).getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, null);
        final PsiElement insertedElement = statement.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        final PsiElement shortenedElement = JavaCodeStyleManager.getInstance(mgr.getProject()).shortenClassReferences(insertedElement);
        codeStyleManager.reformat(shortenedElement);
    }

}

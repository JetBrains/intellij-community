package com.intellij.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public abstract class RenameJavaMemberProcessor extends RenamePsiElementProcessor {
  protected static void qualifyMember(PsiMember member, PsiElement occurence, String newName) throws IncorrectOperationException {
    qualifyMember(occurence, newName, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static void qualifyMember(final PsiElement occurence, final String newName, final PsiClass containingClass, final boolean isStatic)
      throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (isStatic) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(containingClass));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = createQualifiedMemberReference(occurence, newName, containingClass, isStatic);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createMemberReference(PsiMember member, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = member.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String name = member.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, member)) return ref;
    return createQualifiedMemberReference(context, name, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static PsiReferenceExpression createQualifiedMemberReference(final PsiElement context, final String name,
                                                                         final PsiClass containingClass, final boolean isStatic) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    final PsiJavaCodeReferenceElement qualifier;

    final PsiManager manager = containingClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (isStatic) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      if (InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
        return ref;
      }
      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }
}

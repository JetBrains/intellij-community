/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 24.06.2002
 * Time: 18:22:48
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public abstract class ClassMemberReferencesVisitor extends JavaRecursiveElementVisitor {
  private final PsiClass myClass;

  public ClassMemberReferencesVisitor(PsiClass aClass) {
    myClass = aClass;
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
      qualifier.accept(this);
      if (!(qualifier instanceof PsiReferenceExpression)
          || !(((PsiReferenceExpression) qualifier).resolve() instanceof PsiClass)) {
        return;
      }
    }

    PsiElement referencedElement = expression.resolve();

    if (referencedElement instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)referencedElement).getContainingClass();
      if (isPartOf(myClass, containingClass)) {
        visitClassMemberReferenceExpression((PsiMember)referencedElement, expression);
      }
    }
  }

  private static boolean isPartOf(PsiClass elementClass, PsiClass containingClass) {
    if (containingClass == null) return false;
    if (elementClass.equals(containingClass) || elementClass.isInheritor(containingClass, true)) {
      return true;
    } else {
      return false;
    }
  }

  protected void visitClassMemberReferenceExpression(PsiMember classMember,
                                                     PsiReferenceExpression classMemberReferenceExpression) {
    visitClassMemberReferenceElement(classMember, classMemberReferenceExpression);
  }

  protected abstract void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference);

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    PsiElement referencedElement = reference.resolve();
    if (referencedElement instanceof PsiClass) {
      final PsiClass referencedClass = (PsiClass) referencedElement;
      if (PsiTreeUtil.isAncestor(myClass, referencedElement, true)) {
        visitClassMemberReferenceElement((PsiMember)referencedElement, reference);
      }
      else if (isPartOf (myClass, referencedClass.getContainingClass()))
      {
        visitClassMemberReferenceElement((PsiMember)referencedElement, reference);
      }
    }
  }

  protected final PsiClass getPsiClass() {
    return myClass;
  }
}

package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class ElementNeedsThis extends ClassThisReferencesVisitor {
  private boolean myResult = false;
  private PsiElement myMember;

  public ElementNeedsThis(PsiClass aClass, PsiElement member) {
    super(aClass);
    myMember = member;
  }

  public ElementNeedsThis(PsiClass aClass) {
    super(aClass);
    myMember = null;
  }
  public boolean usesMembers() {
    return myResult;
  }

  protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
    if (classMember == null || classMember.equals(myMember)) return;
    if (classMember.hasModifierProperty(PsiModifier.STATIC)) return;

    myResult = true;
  }

  protected void visitExplicitThis(PsiClass referencedClass, PsiThisExpression reference) {
    myResult = true;
  }

  protected void visitExplicitSuper(PsiClass referencedClass, PsiSuperExpression reference) {
    myResult = true;
  }

  @Override public void visitElement(PsiElement element) {
    if (myResult) return;
    super.visitElement(element);
  }
}

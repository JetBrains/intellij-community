package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author ven
 */
class ReferencedElementsCollector extends PsiRecursiveElementVisitor {
  final HashSet<PsiMember> myReferencedMembers = new HashSet<PsiMember>();

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiMember) {
      checkAddMember((PsiMember)psiElement);
    }
  }

  protected void checkAddMember(@NotNull final PsiMember member) {
    myReferencedMembers.add(member);
  }
}

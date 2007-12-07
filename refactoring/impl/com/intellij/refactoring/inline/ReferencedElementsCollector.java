package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author ven
 */
class ReferencedElementsCollector extends JavaRecursiveElementVisitor {
  final HashSet<PsiMember> myReferencedMembers = new HashSet<PsiMember>();

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    final PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiMember) {
      checkAddMember((PsiMember)psiElement);
    }
  }

  protected void checkAddMember(@NotNull final PsiMember member) {
    myReferencedMembers.add(member);
  }
}

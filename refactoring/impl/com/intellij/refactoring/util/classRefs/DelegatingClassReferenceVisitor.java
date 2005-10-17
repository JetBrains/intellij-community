package com.intellij.refactoring.util.classRefs;

import com.intellij.psi.*;

/**
 * @author dsl
 */
public class DelegatingClassReferenceVisitor implements ClassReferenceVisitor {
  private final ClassReferenceVisitor myDelegate;

  public DelegatingClassReferenceVisitor(ClassReferenceVisitor delegate) {

    myDelegate = delegate;
  }

  public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
    myDelegate.visitReferenceExpression(referenceExpression);
  }

  public void visitLocalVariableDeclaration(PsiLocalVariable variable, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitLocalVariableDeclaration(variable, occurence);
  }

  public void visitFieldDeclaration(PsiField field, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitFieldDeclaration(field, occurence);
  }

  public void visitParameterDeclaration(PsiParameter parameter, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitParameterDeclaration(parameter, occurence);
  }

  public void visitMethodReturnType(PsiMethod method, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitMethodReturnType(method, occurence);
  }

  public void visitTypeCastExpression(PsiTypeCastExpression typeCastExpression, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitTypeCastExpression(typeCastExpression, occurence);
  }

  public void visitNewExpression(PsiNewExpression newExpression, ClassReferenceVisitor.TypeOccurence occurence) {
    myDelegate.visitNewExpression(newExpression, occurence);
  }

  public void visitOther(PsiElement ref) {
    myDelegate.visitOther(ref);
  }

}

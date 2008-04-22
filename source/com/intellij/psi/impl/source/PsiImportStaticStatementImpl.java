package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiImportStaticStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStaticStatement {
  public static final PsiImportStaticStatementImpl[] EMPTY_ARRAY = new PsiImportStaticStatementImpl[0];

  public PsiImportStaticStatementImpl(final PsiImportStatementStub stub) {
    super(stub);
  }

  public PsiImportStaticStatementImpl(final ASTNode node) {
    super(node);
  }

  public PsiClass resolveTargetClass() {
    final PsiJavaCodeReferenceElement classReference = getClassReference();
    if (classReference == null) return null;
    final PsiElement result = classReference.resolve();
    if (result instanceof PsiClass) {
      return (PsiClass) result;
    }
    else {
      return null;
    }
  }

  public String getReferenceName() {
    if (isOnDemand()) return null;
    final PsiImportStaticReferenceElement memberReference = getMemberReference();
    if (memberReference != null) {
      return memberReference.getReferenceName();
    }
    else {
      return null;
    }
  }

  @Nullable
  private PsiImportStaticReferenceElement getMemberReference() {
    if (isOnDemand()) {
      return null;
    }
    else {
      return (PsiImportStaticReferenceElement) getImportReference();
    }
  }

  @Nullable
  private PsiJavaCodeReferenceElement getClassReference() {
    if (isOnDemand()) {
      return getImportReference();
    }
    else {
      final PsiImportStaticReferenceElement memberReference = getMemberReference();
      if (memberReference != null) {
        return memberReference.getClassReference();
      }
      else {
        return null;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStaticStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiImportStaticStatement";
  }
}
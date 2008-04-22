package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import org.jetbrains.annotations.NotNull;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {
  public static final PsiImportStatementImpl[] EMPTY_ARRAY = new PsiImportStatementImpl[0];

  public PsiImportStatementImpl(final PsiImportStatementStub stub) {
    super(stub);
  }

  public PsiImportStatementImpl(final ASTNode node) {
    super(node);
  }

  public String getQualifiedName() {
    final PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.getCanonicalText();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiImportStatement";
  }
}
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {
  public static final PsiImportStatementImpl[] EMPTY_ARRAY = new PsiImportStatementImpl[0];
  public static final ArrayFactory<PsiImportStatementImpl> ARRAY_FACTORY = new ArrayFactory<PsiImportStatementImpl>() {
    public PsiImportStatementImpl[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiImportStatementImpl[count];
    }
  };

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
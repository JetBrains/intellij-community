package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiAnnotationMethodImpl extends PsiMethodImpl implements PsiAnnotationMethod {
  public PsiAnnotationMethodImpl(final PsiMethodStub stub) {
    super(stub);
  }

  public PsiAnnotationMethodImpl(final ASTNode node) {
    super(node);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.ABSTRACT.equals(name) || PsiModifier.PUBLIC.equals(name) || super.hasModifierProperty(name);
  }

  public PsiAnnotationMemberValue getDefaultValue() {
    final ASTNode node = getNode().findChildByRole(ChildRole.ANNOTATION_DEFAULT_VALUE);
    if (node == null) return null;
    return (PsiAnnotationMemberValue)node.getPsi();
  }

  @NonNls
  public String toString() {
    return "PsiAnnotationMethod:" + getName();
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
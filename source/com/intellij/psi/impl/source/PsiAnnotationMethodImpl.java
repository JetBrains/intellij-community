package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class PsiAnnotationMethodImpl extends PsiMethodImpl implements PsiAnnotationMethod {
  public PsiAnnotationMethodImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiAnnotationMethodImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public boolean hasModifierProperty(String name) {
    if (PsiModifier.FINAL.equals(name)) return true;
    return super.hasModifierProperty(name);
  }

  public PsiAnnotationMemberValue getDefaultValue() {
    final ASTNode node = calcTreeElement().findChildByRole(ChildRole.ANNOTATION_DEFAULT_VALUE);
    if (node == null) return null;
    return (PsiAnnotationMemberValue)node.getPsi();
  }

  @NonNls
  public String toString() {
    return "PsiAnnotationMethod:" + getName();
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationMethod(this);
  }
}

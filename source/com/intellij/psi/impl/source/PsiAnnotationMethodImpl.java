package com.intellij.psi.impl.source;

import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.ChildRole;

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

  public PsiAnnotationMemberValue getDefaultValue() {
    return (PsiAnnotationMemberValue)calcTreeElement().findChildByRole(ChildRole.ANNOTATION_DEFAULT_VALUE);
  }

  public String toString() {
    return "PsiAnnotationMethod:" + getName();
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationMethod(this);
  }
}

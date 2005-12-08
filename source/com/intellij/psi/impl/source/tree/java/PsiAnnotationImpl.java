package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends CompositePsiElement implements PsiModifier, PsiAnnotation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl");

  public PsiAnnotationImpl() {
    super(ANNOTATION);
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return (PsiAnnotationParameterList)findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
  }

  @Nullable public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == ANNOTATION_PARAMETER_LIST) {
      return ChildRole.PARAMETER_LIST;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.CLASS_REFERENCE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.PARAMETER_LIST:
        return TreeUtil.findChild(this, ANNOTATION_PARAMETER_LIST);

      case ChildRole.CLASS_REFERENCE:
        return TreeUtil.findChild(this, JAVA_CODE_REFERENCE);
    }
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }
}

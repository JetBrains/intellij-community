package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.PsiImplUtil;

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

  public PsiAnnotationParameterList getParameterList() {
    return (PsiAnnotationParameterList)findChildByRoleAsPsiElement(ChildRole.PARAMETER_LIST);
  }

  public int getChildRole(TreeElement child) {
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

  public TreeElement findChildByRole(int role) {
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

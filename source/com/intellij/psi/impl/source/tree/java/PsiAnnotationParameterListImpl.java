package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

/**
 * @author ven
 */
public class PsiAnnotationParameterListImpl extends CompositePsiElement implements PsiAnnotationParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAnnotationParameterListImpl");
  private PsiNameValuePair[] myCachedMembers = null;

  public PsiAnnotationParameterListImpl() {
    super(ANNOTATION_PARAMETER_LIST);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedMembers = null;
  }

  public PsiNameValuePair[] getAttributes() {
    if (myCachedMembers == null) {
      myCachedMembers = (PsiNameValuePair[])getChildrenAsPsiElements(NAME_VALUE_PAIR_BIT_SET, PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR);
    }

    return myCachedMembers;
  }

  public int getChildRole(TreeElement child) {
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (ANNOTATION_MEMBER_VALUE_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.ANNOTATION_VALUE;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public TreeElement findChildByRole(int role) {
    switch (role) {
      default:
        LOG.assertTrue(false);
      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);
    }
  }

  public String toString() {
    return "PsiAnnotationParameterList";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationParameterList(this);
  }
}

package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

/**
 * @author ven
 */
public class PsiArrayInitializerMemberValueImpl extends CompositePsiElement implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl");
  public PsiArrayInitializerMemberValueImpl() {
    super(ANNOTATION_ARRAY_INITIALIZER);
  }

  public PsiAnnotationMemberValue[] getInitializers() {
    return (PsiAnnotationMemberValue[])getChildrenAsPsiElements(ANNOTATION_MEMBER_VALUE_BIT_SET,
                                                                PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return TreeUtil.findChild(this, LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChild(this, RBRACE);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LBRACE) {
      return ChildRole.LBRACE;
    }
    else if (i == RBRACE) {
      return ChildRole.RBRACE;
    }
    else {
      if (ANNOTATION_MEMBER_VALUE_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.ANNOTATION_VALUE;
      }
      return ChildRole.NONE;
    }
  }

  public String toString(){
    return "PsiArrayInitializerMemerValue:" + getText();
  }

  public final void accept(PsiElementVisitor visitor) {
    visitor.visitAnnotationArrayInitializer(this);
  }
}

package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiArrayInitializerMemberValueImpl extends PsiCommaSeparatedListImpl implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl");
  public PsiArrayInitializerMemberValueImpl() {
    super(ANNOTATION_ARRAY_INITIALIZER, ANNOTATION_MEMBER_VALUE_BIT_SET);
  }

  @NotNull
  public PsiAnnotationMemberValue[] getInitializers() {
    return getChildrenAsPsiElements(ANNOTATION_MEMBER_VALUE_BIT_SET, PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR);
  }

  public ASTNode findChildByRole(int role) {
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

  public int getChildRole(ASTNode child) {
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
      if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
        return ChildRole.ANNOTATION_VALUE;
      }
      return ChildRoleBase.NONE;
    }
  }

  public String toString(){
    return "PsiArrayInitializerMemberValue:" + getText();
  }

  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationArrayInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}

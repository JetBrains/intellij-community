package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class PsiArrayInitializerExpressionImpl extends CompositePsiElement implements PsiArrayInitializerExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl");

  public PsiArrayInitializerExpressionImpl() {
    super(ARRAY_INITIALIZER_EXPRESSION);
  }

  public PsiExpression[] getInitializers(){
    return (PsiExpression[])getChildrenAsPsiElements(EXPRESSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
  }

  public PsiType getType(){
    if (getTreeParent() instanceof PsiNewExpression){
      if (getTreeParent().getChildRole(this) == ChildRole.ARRAY_INITIALIZER){
        return ((PsiNewExpression)getTreeParent()).getType();
      }
    }
    else if (getTreeParent() instanceof PsiVariable){
      return ((PsiVariable)getTreeParent()).getType();
    }
    else if (getTreeParent() instanceof PsiArrayInitializerExpression){
      PsiType parentType = ((PsiArrayInitializerExpression)getTreeParent()).getType();
      if (!(parentType instanceof PsiArrayType)) return null;
      return ((PsiArrayType) parentType).getComponentType();
    }
    else if (getTreeParent() instanceof FieldElement){
      return ((PsiField)getParent()).getType();
    }

    return null;
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
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitArrayInitializerExpression(this);
  }

  public String toString(){
    return "PsiArrayInitializerExpression:" + getText();
  }
}

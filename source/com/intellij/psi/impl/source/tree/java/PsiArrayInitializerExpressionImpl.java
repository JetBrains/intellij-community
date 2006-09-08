package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class PsiArrayInitializerExpressionImpl extends CompositePsiElement implements PsiArrayInitializerExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl");

  public PsiArrayInitializerExpressionImpl() {
    super(ARRAY_INITIALIZER_EXPRESSION);
  }

  @NotNull
  public PsiExpression[] getInitializers(){
    return getChildrenAsPsiElements(EXPRESSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
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
      final PsiType componentType = ((PsiArrayType)parentType).getComponentType();
      return componentType instanceof PsiArrayType ? componentType : null;
    }
    else if (getTreeParent() instanceof FieldElement){
      return ((PsiField)getParent()).getType();
    }

    return null;
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
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
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

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (ElementType.EXPRESSION_BIT_SET.contains(first.getElementType())) {
     final CharTable charTab = SharedImplUtil.findCharTableByTree(this);
      ASTNode element = first;
      for (ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, charTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, charTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }
}

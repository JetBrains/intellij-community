package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class PsiReferenceParameterListImpl extends CompositePsiElement implements PsiReferenceParameterList{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl");

  public PsiReferenceParameterListImpl() {
    super(REFERENCE_PARAMETER_LIST);
  }

  @NotNull
  public PsiTypeElement[] getTypeParameterElements() {
    return getChildrenAsPsiElements(TYPES_BIT_SET, PSI_TYPE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByReferenceParameterList(this);
  }

  public int getChildRole(ASTNode child) {
    IElementType i = child.getElementType();
    if (i == TYPE) {
      return ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST;
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (i == GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch(role){
      default:
        return null;

      case ChildRole.LT_IN_TYPE_LIST:
        if (getFirstChildNode() != null && getFirstChildNode().getElementType() == LT){
          return getFirstChildNode();
        }
        else{
          return null;
        }

      case ChildRole.GT_IN_TYPE_LIST:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == GT){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    if (first == last && first.getElementType() == TYPE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (getFirstChildNode()== null || getFirstChildNode().getElementType() != LT){
      TreeElement lt = Factory.createSingleLeafElement(LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }
    if (getLastChildNode() == null || getLastChildNode().getElementType() != GT){
      TreeElement gt = Factory.createSingleLeafElement(GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == TYPE){
      ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == TYPE){
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA){
          deleteChildInternal(prev);
        }
      }
    }

    super.deleteChildInternal(child);

    if (getTypeParameterElements().length == 0){
      ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      if (lt != null){
        deleteChildInternal(lt);
      }

      ASTNode gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
      if (gt != null){
        deleteChildInternal(gt);
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }
}

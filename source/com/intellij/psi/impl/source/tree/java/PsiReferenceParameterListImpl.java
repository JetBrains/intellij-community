package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;

/**
 *  @author dsl
 */
public class PsiReferenceParameterListImpl extends CompositePsiElement implements PsiReferenceParameterList{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl");

  public PsiReferenceParameterListImpl() {
    super(REFERENCE_PARAMETER_LIST);
  }

  public PsiTypeElement[] getTypeParameterElements() {
    return (PsiTypeElement[]) getChildrenAsPsiElements(TYPES_BIT_SET, PSI_TYPE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByReferenceParameterList(this);
  }

  public int getChildRole(TreeElement child) {
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

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch(role){
      default:
        return null;

      case ChildRole.LT_IN_TYPE_LIST:
        if (firstChild != null && firstChild.getElementType() == LT){
          return firstChild;
        }
        else{
          return null;
        }

      case ChildRole.GT_IN_TYPE_LIST:
        if (lastChild != null && lastChild.getElementType() == GT){
          return lastChild;
        }
        else{
          return null;
        }
    }
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before){
    if (first == last && first.getElementType() == TYPE){
      if (lastChild != null && lastChild.getElementType() == ERROR_ELEMENT){
        super.deleteChildInternal(lastChild);
      }
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
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (first == last && first.getElementType() == TYPE){
      TreeElement element = first;
      for(TreeElement child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(TreeElement child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    if (firstChild.getElementType() != LT){
      TreeElement lt = Factory.createSingleLeafElement(LT, new char[]{'<'}, 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, firstChild, Boolean.TRUE);
    }
    if (lastChild.getElementType() != GT){
      TreeElement gt = Factory.createSingleLeafElement(GT, new char[]{'>'}, 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, lastChild, Boolean.FALSE);
    }
    return firstAdded;
  }

  public void deleteChildInternal(TreeElement child) {
    if (child.getElementType() == TYPE){
      TreeElement next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        TreeElement prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA){
          deleteChildInternal(prev);
        }
      }
    }

    super.deleteChildInternal(child);

    if (getTypeParameterElements().length == 0){
      TreeElement lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      if (lt != null){
        deleteChildInternal(lt);
      }

      TreeElement gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
      if (gt != null){
        deleteChildInternal(gt);
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceParameterList(this);
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }
}

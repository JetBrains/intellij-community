package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.CharTable;

public class PsiExpressionListImpl extends CompositePsiElement implements PsiExpressionList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl");

  public PsiExpressionListImpl() {
    super(EXPRESSION_LIST);
  }

  public PsiExpression[] getExpressions() {
    return (PsiExpression[])getChildrenAsPsiElements(EXPRESSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        return firstChild != null && firstChild.getElementType() == LPARENTH ? firstChild : null;

      case ChildRole.RPARENTH:
        if (lastChild != null && lastChild.getElementType() == RPARENTH) {
          return lastChild;
        }
        else {
          return null;
        }
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
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
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRole.NONE;
    }
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before) {
    TreeElement firstAdded = null;
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(LPARENTH, new char[]{'('}, 0, 1, treeCharTab, getManager());
          firstAdded = super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(RPARENTH, new char[]{')'}, 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.RPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByRole(ChildRole.LPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(LPARENTH, new char[]{'('}, 0, 1, treeCharTab, getManager());
          firstAdded = super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(RPARENTH, new char[]{')'}, 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.LPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.FALSE;
      }
    }
    if(firstAdded != null) firstAdded = super.addInternal(first, last, anchor, before);
    else firstAdded = super.addInternal(first, last, anchor, before);
    if (first == last && ElementType.EXPRESSION_BIT_SET.isInSet(first.getElementType())) {
      TreeElement element = first;
      for (TreeElement child = element.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for (TreeElement child = element.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  public void deleteChildInternal(TreeElement child) {
    if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
      TreeElement next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA) {
        deleteChildInternal(next);
      }
      else {
        TreeElement prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA) {
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitExpressionList(this);
  }

  public String toString() {
    return "PsiExpressionList";
  }
}

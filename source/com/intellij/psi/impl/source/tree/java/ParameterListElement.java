package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

public class ParameterListElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ParameterListElement");

  public ParameterListElement() {
    super(PARAMETER_LIST);
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RPARENTH);
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByRole(ChildRole.LPARENTH);
        before = Boolean.FALSE;
      }
    }
    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == PARAMETER) {
      TreeElement element = first;
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      for (TreeElement child = element.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == PARAMETER) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for (TreeElement child = element.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == PARAMETER) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    //todo[max] hack?
    try {
      CodeStyleManager.getInstance(getManager().getProject()).reformat(getPsiElement());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return firstAdded;
  }

  public void deleteChildInternal(TreeElement child) {
    if (child.getElementType() == PARAMETER) {
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

    //todo[max] hack?
    try {
      CodeStyleManager.getInstance(getManager().getProject()).reformat(getPsiElement());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        if (firstChild.getElementType() == LPARENTH) {
          return firstChild;
        }
        else {
          return null;
        }

      case ChildRole.RPARENTH:
        if (lastChild.getElementType() == RPARENTH) {
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
    if (i == PARAMETER) {
      return ChildRole.PARAMETER;
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LPARENTH) {
      return getChildRole(child, ChildRole.LPARENTH);
    }
    else if (i == RPARENTH) {
      return getChildRole(child, ChildRole.RPARENTH);
    }
    else {
      return ChildRole.NONE;
    }
  }
}

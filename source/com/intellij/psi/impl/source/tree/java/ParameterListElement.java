package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ParameterListElement extends RepositoryTreeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ParameterListElement");

  public ParameterListElement() {
    super(PARAMETER_LIST);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
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
      ASTNode element = first;
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      for (ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == PARAMETER) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == PARAMETER) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
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

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == PARAMETER) {
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA) {
        deleteChildInternal(next);
      }
      else {
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
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

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    ChameleonTransforming.transformChildren(this);
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        if (getFirstChildNode().getElementType() == LPARENTH) {
          return getFirstChildNode();
        }
        else {
          return null;
        }

      case ChildRole.RPARENTH:
        if (getLastChildNode().getElementType() == RPARENTH) {
          return getLastChildNode();
        }
        else {
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
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
      return ChildRoleBase.NONE;
    }
  }
}

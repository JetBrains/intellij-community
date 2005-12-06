package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  public PsiNameValuePair[] getAttributes() {
    if (myCachedMembers == null) {
      myCachedMembers = getChildrenAsPsiElements(NAME_VALUE_PAIR_BIT_SET, PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR);
    }

    return myCachedMembers;
  }

  public int getChildRole(ASTNode child) {
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

  public ASTNode findChildByRole(int role) {
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

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {

    if (first.getElementType() == NAME_VALUE_PAIR && last.getElementType() == NAME_VALUE_PAIR) {
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      ASTNode lparenth = findChildByRole(ChildRole.LPARENTH);
      if (lparenth == null) {
        LeafElement created = Factory.createSingleLeafElement(LPARENTH, new char[]{'('}, 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getFirstChildNode(), true);
      }
      ASTNode rparenth = findChildByRole(ChildRole.RPARENTH);
      if (rparenth == null) {
        LeafElement created = Factory.createSingleLeafElement(RPARENTH, new char[]{')'}, 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getLastChildNode(), false);
      }

      if (anchor == null) {
        if (before == null || before.booleanValue()) {
          anchor = findChildByRole(ChildRole.LPARENTH);
          before = Boolean.FALSE;
        }
        else {
          anchor = findChildByRole(ChildRole.RPARENTH);
          before = Boolean.TRUE;
        }
      }

      final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

      for (ASTNode child = ((ASTNode)first).getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == NAME_VALUE_PAIR) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }

      for (ASTNode child = ((ASTNode)first).getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == NAME_VALUE_PAIR) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(ASTNode child) {
    if (child.getElementType() == NAME_VALUE_PAIR) {
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA) {
        deleteChildInternal(next);
      }
      else {
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA) {
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
  }
}

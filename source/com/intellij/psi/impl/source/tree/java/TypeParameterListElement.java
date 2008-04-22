package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class TypeParameterListElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.TypeParameterListElement");

  public TypeParameterListElement() {
    super(ElementType.TYPE_PARAMETER_LIST);
  }

  public int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType elType = child.getElementType();
    if (elType == TYPE_PARAMETER) {
      return ChildRole.TYPE_PARAMETER_IN_LIST;
    }
    else if (elType == COMMA) {
      return ChildRole.COMMA;
    }
    else if (elType == LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (elType == GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public TreeElement addInternal(final TreeElement first, final ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    TreeElement lt = (TreeElement)findChildByRole(ChildRole.LT_IN_TYPE_LIST);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (lt == null) {
      lt = Factory.createSingleLeafElement(LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }

    TreeElement gt = (TreeElement)findChildByRole(ChildRole.GT_IN_TYPE_LIST);
    if (gt == null) {
      gt = Factory.createSingleLeafElement(GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null) {
      if (before == null || before.booleanValue()){
        anchor = gt;
        before = Boolean.TRUE;
      }
      else{
        anchor = lt;
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == TYPE_PARAMETER) {
      final ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE_PARAMETER){
          final TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE_PARAMETER){
          final TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  public void deleteChildInternal(@NotNull final ASTNode child) {
    if (child.getElementType() == TYPE_PARAMETER){
      final ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        final ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA){
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
    if (child.getElementType() == TYPE_PARAMETER) {
      final ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      final ASTNode next = TreeUtil.skipElements(lt.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == GT) {
        deleteChildInternal(lt);
        deleteChildInternal(next);
      }
    }
  }
}

package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

/**
 * @author max
 */
public class TypeParameterListElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.TypeParameterListElement");

  public TypeParameterListElement() {
    super(ElementType.TYPE_PARAMETER_LIST);
  }

  public int getChildRole(final TreeElement child) {
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
      return ChildRole.NONE;
    }
  }

  public TreeElement addInternal(final TreeElement first, final TreeElement last, TreeElement anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    TreeElement lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (lt == null) {
      lt = Factory.createSingleLeafElement(LT, new char[]{'<'}, 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, firstChild, Boolean.TRUE);
    }

    TreeElement gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
    if (gt == null) {
      gt = Factory.createSingleLeafElement(GT, new char[]{'>'}, 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, lastChild, Boolean.FALSE);
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
      final TreeElement element = first;
      for(TreeElement child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE_PARAMETER){
          final TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(TreeElement child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == TYPE_PARAMETER){
          final TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  public void deleteChildInternal(final TreeElement child) {
    if (child.getElementType() == TYPE_PARAMETER){
      final TreeElement next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        final TreeElement prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA){
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
    if (child.getElementType() == TYPE_PARAMETER) {
      final TreeElement lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      final TreeElement next = TreeUtil.skipElements(lt.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == GT) {
        deleteChildInternal(lt);
        deleteChildInternal(next);
      }
    }
  }
}

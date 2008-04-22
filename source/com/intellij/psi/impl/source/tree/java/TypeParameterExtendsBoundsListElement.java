package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class TypeParameterExtendsBoundsListElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.TypeParameterExtendsBoundsListElement");

  public TypeParameterExtendsBoundsListElement() {
    super(JavaElementType.EXTENDS_BOUND_LIST);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == AND) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(AND, "&", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == AND) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(AND, "&", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    final IElementType keywordType = JavaTokenType.EXTENDS_KEYWORD;
    final String keywordText = PsiKeyword.EXTENDS;
    if (TreeUtil.findChild(this, keywordType) == null && TreeUtil.findChild(this, JAVA_CODE_REFERENCE) != null){
      LeafElement keyword = Factory.createSingleLeafElement(keywordType, keywordText, 0, keywordText.length(), treeCharTab, getManager());
      super.addInternal(keyword, keyword, getFirstChildNode(), Boolean.TRUE);
    }
    return firstAdded;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JAVA_CODE_REFERENCE){
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == AND){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null){
          if (prev.getElementType() == AND || prev.getElementType() == EXTENDS_KEYWORD){
            deleteChildInternal(prev);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  public int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    final IElementType elType = child.getElementType();
    if (elType == AND) {
      return ChildRole.AMPERSAND_IN_BOUNDS_LIST;
    }
    else if (elType == ElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.BASE_CLASS_REFERENCE;
    }
    else if (elType == JavaTokenType.EXTENDS_KEYWORD) {
      return ChildRole.EXTENDS_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}

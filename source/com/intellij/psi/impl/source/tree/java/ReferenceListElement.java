package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceListElement extends RepositoryTreeElement implements Constants {
  public ReferenceListElement(IElementType type) {
    super(type);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
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
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    IElementType keywordType;
    String keywordText;
    keywordType = getKeywordType();
    keywordText = getKeywordText();
    if (TreeUtil.findChild(this, keywordType) == null && TreeUtil.findChild(this, JAVA_CODE_REFERENCE) != null){
      LeafElement keyword = Factory.createSingleLeafElement(keywordType, keywordText, 0, keywordText.length(), SharedImplUtil.findCharTableByTree(this), getManager());
      super.addInternal(keyword, keyword, getFirstChildNode(), Boolean.TRUE);
    }
    return firstAdded;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JAVA_CODE_REFERENCE){
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null){
          if (prev.getElementType() == COMMA
              || prev.getElementType() == getKeywordType()
              ){
            deleteChildInternal(prev);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  protected abstract String getKeywordText();

  protected abstract IElementType getKeywordType();
}

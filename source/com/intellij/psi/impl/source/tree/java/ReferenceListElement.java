package com.intellij.psi.impl.source.tree.java;

import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public abstract class ReferenceListElement extends RepositoryTreeElement{
  public ReferenceListElement(IElementType type) {
    super(type);
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before){
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      if (lastChild != null && lastChild.getElementType() == ERROR_ELEMENT){
        super.deleteChildInternal(lastChild);
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      TreeElement element = first;
      for(TreeElement child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(TreeElement child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == COMMA) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(COMMA, new char[]{','}, 0, 1, treeCharTab, getManager());
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
      LeafElement keyword = Factory.createSingleLeafElement(keywordType, keywordText.toCharArray(), 0, keywordText.length(), SharedImplUtil.findCharTableByTree(this), getManager());
      super.addInternal(keyword, keyword, firstChild, Boolean.TRUE);
    }
    return firstAdded;
  }

  public void deleteChildInternal(TreeElement child) {
    if (child.getElementType() == JAVA_CODE_REFERENCE){
      TreeElement next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA){
        deleteChildInternal(next);
      }
      else{
        TreeElement prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
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

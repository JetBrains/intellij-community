/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * Adds or removes comma
 * @author ven
 */
public abstract class PsiCommaSeparatedListImpl extends CompositePsiElement implements Constants {
  private TokenSet myTypesOfElements;


  protected PsiCommaSeparatedListImpl(IElementType type, final TokenSet typeOfElements) {
    super(type);
    myTypesOfElements = typeOfElements;
  }


  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (myTypesOfElements.contains(first.getElementType()) && myTypesOfElements.contains(last.getElementType())) {
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
      for (ASTNode child = ((ASTNode)first).getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (myTypesOfElements.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }

      for (ASTNode child = ((ASTNode)first).getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (myTypesOfElements.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }


  public void deleteChildInternal(@NotNull ASTNode child) {
    if (myTypesOfElements.contains(child.getElementType())) {
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
  }
}

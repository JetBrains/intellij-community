package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.openapi.diagnostic.Logger;

public class ModifierListElement extends RepositoryTreeElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ModifierListElement");

  public ModifierListElement() {
    super(MODIFIER_LIST);
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before) {
    if (before == null){
      if (first == last && ElementType.KEYWORD_BIT_SET.isInSet(first.getElementType())){
        anchor = CodeEditUtil.getDefaultAnchor((PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(this),
                                               (PsiKeyword)SourceTreeToPsiMap.treeElementToPsi(first));
        before = Boolean.TRUE;
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == JavaElementType.ANNOTATION) return ChildRole.ANNOTATION;
    return ChildRole.NONE;
  }
}

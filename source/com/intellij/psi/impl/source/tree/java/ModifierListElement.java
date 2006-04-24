package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.ASTNode;
import com.intellij.util.containers.HashMap;

public class ModifierListElement extends RepositoryTreeElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ModifierListElement");

  public ModifierListElement() {
    super(MODIFIER_LIST);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (before == null){
      if (first == last && ElementType.KEYWORD_BIT_SET.contains(first.getElementType())){
        anchor = getDefaultAnchor((PsiModifierList)SourceTreeToPsiMap.treeElementToPsi(this),
                                  (PsiKeyword)SourceTreeToPsiMap.treeElementToPsi(first));
        before = Boolean.TRUE;
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == JavaElementType.ANNOTATION) return ChildRole.ANNOTATION;
    return ChildRole.NONE;
  }

  private static final HashMap<String, Integer> ourModifierToOrderMap = new HashMap<String, Integer>();

  static { //TODO : options?
    ourModifierToOrderMap.put(PsiModifier.PUBLIC, 1);
    ourModifierToOrderMap.put(PsiModifier.PRIVATE, 1);
    ourModifierToOrderMap.put(PsiModifier.PROTECTED, 1);
    ourModifierToOrderMap.put(PsiModifier.STATIC, 2);
    ourModifierToOrderMap.put(PsiModifier.ABSTRACT, 2);
    ourModifierToOrderMap.put(PsiModifier.FINAL, 3);
    ourModifierToOrderMap.put(PsiModifier.SYNCHRONIZED, 4);
    ourModifierToOrderMap.put(PsiModifier.TRANSIENT, 4);
    ourModifierToOrderMap.put(PsiModifier.VOLATILE, 4);
    ourModifierToOrderMap.put(PsiModifier.NATIVE, 5);
    ourModifierToOrderMap.put(PsiModifier.STRICTFP, 6);
  }

  private static ASTNode getDefaultAnchor(PsiModifierList modifierList, PsiKeyword modifier) {
    Integer order = ourModifierToOrderMap.get(modifier.getText());
    if (order == null) return null;
    for (ASTNode child = SourceTreeToPsiMap.psiElementToTree(modifierList).getFirstChildNode(); child != null; child = child.getTreeNext())
    {
      if (ElementType.KEYWORD_BIT_SET.contains(child.getElementType())) {
        Integer order1 = ourModifierToOrderMap.get(child.getText());
        if (order1 == null) continue;
        if (order1.intValue() > order.intValue()) {
          return child;
        }
      }
    }
    return null;
  }
}

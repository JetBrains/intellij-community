package com.intellij.psi.impl.source;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  public PsiExpressionCodeFragmentImpl(PsiManagerImpl manager,
                                       boolean isPhysical,
                                       String name,
                                       char[] text,
                                       int startOffset,
                                       int endOffset) {
    super(manager, ElementType.EXPRESSION_TEXT, isPhysical, name, text, startOffset, endOffset);
  }

  public PsiExpression getExpression() {
    ChameleonTransforming.transformChildren(calcTreeElement());
    TreeElement exprChild = TreeUtil.findChild(calcTreeElement(), EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }
}

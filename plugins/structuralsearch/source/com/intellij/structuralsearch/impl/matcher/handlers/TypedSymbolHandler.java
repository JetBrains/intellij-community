package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Search handler for typed symbol ('T<a*>)
 */
public class TypedSymbolHandler extends MatchingHandler {
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) {
      return false;
    }

    return context.getMatcher().match(
      patternNode.getFirstChild(),
      matchedNode
    );
  }
}

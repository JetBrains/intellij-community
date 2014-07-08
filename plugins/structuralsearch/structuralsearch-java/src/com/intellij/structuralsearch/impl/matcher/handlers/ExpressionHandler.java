package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Handler for substitution expression search
 */
public class ExpressionHandler extends MatchingHandler {
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) {
      return false;
    }

    return context.getMatcher().match(
      ((PsiExpressionStatement)patternNode).getExpression(),
      matchedNode
    );
  }
}

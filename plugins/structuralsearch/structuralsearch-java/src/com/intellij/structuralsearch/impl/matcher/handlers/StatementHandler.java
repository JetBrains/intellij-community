package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

/**
 * Handler for statement search
 */
public class StatementHandler extends MatchingHandler {

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    // filtering is done on SubstituionHandler level
    if (patternNode==null) return false;
    patternNode = ((PsiExpressionStatement)patternNode).getExpression();

    /*if (matchedNode instanceof PsiExpressionStatement) {
      //matchedNode = ((PsiExpressionStatement)matchedNode).getExpression();
    } else*/ if (( !(matchedNode instanceof PsiStatement) &&
                 !(matchedNode instanceof PsiComment) // comments to be matched as statements
               ) ||
        ( matchedNode instanceof PsiBlockStatement &&
          !(matchedNode.getParent() instanceof PsiBlockStatement) &&
          !(matchedNode.getParent().getParent() instanceof PsiSwitchStatement)
        )) {
      // typed statement does not match this things
      // (BlockStatement could be nontop level in if, etc)
      return false;
    }

    return context.getMatcher().match(patternNode,matchedNode);
  }
}

package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;

/**
 * Handler for value read
 */
public final class ReadPredicate extends MatchPredicate {
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (matchedNode instanceof PsiIdentifier) {
      matchedNode = matchedNode.getParent();
    }
    if (matchedNode instanceof PsiReferenceExpression &&
        ( !(matchedNode.getParent() instanceof PsiMethodCallExpression) &&
          ( !(matchedNode.getParent() instanceof PsiAssignmentExpression) ||
            ((PsiAssignmentExpression)matchedNode.getParent()).getLExpression() != matchedNode
          )
        ) &&
        MatchUtils.getReferencedElement(matchedNode) instanceof PsiVariable
       ) {
      return true;
    }
    return false;
  }
}

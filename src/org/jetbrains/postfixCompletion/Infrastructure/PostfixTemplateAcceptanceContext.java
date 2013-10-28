package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.PsiExpression;

import java.util.ArrayList;
import java.util.List;

public final class PostfixTemplateAcceptanceContext {
  private List<PrefixExpressionContext> myExpressions;

  public PostfixTemplateAcceptanceContext(PsiExpression expression) {
    myExpressions = new ArrayList<>();

  }

  public List<PrefixExpressionContext> getExpressions() {
    return myExpressions;
  }
}
/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.lookup.LookupItem;

public class EmptyExpression implements Expression {
  public EmptyExpression() {
  }

  public Result calculateResult(ExpressionContext context) {
    return null;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    return null;
  }

}
package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;

public interface Macro {
  String getName();

  String getDescription ();

  String getDefaultValue();

  Result calculateResult(Expression[] params, ExpressionContext context);

  Result calculateQuickResult(Expression[] params, ExpressionContext context);

  LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context);
}

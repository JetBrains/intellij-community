package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Key;

public interface Expression {
  Key AUTO_POPUP_NEXT_LOOKUP = Key.create("AUTO_POPUP_NEXT_LOOKUP");
  Result calculateResult(ExpressionContext context);
  Result calculateQuickResult(ExpressionContext context);
  LookupItem[] calculateLookupItems(ExpressionContext context);
}


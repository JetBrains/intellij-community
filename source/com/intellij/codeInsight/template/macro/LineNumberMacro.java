package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;

public class LineNumberMacro implements Macro{
  public String getName() {
    return "lineNumber";
  }

  public String getDescription() {
    return "lineNumber()";
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    final int offset = context.getStartOffset();
    int line = context.getEditor().offsetToLogicalPosition(offset).line + 1;
    return new TextResult("" + line);
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    return new LookupItem[0];
  }

}
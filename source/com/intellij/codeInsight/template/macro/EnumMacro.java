package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import java.util.LinkedHashSet;

public class EnumMacro implements Macro{
  public String getName() {
    return "enum";
  }

  public String getDescription() {
    return "enum(...)";
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    if (params == null || params.length == 0) return null;
    Result result = params[0].calculateResult(context);
    return result;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    if (params == null || params.length == 0) return null;
    Result result = params[0].calculateQuickResult(context);
    return result;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    if (params == null || params.length ==0) return null;
    LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();

    for(int i = 0; i < params.length; i++){
      Result object = params[i].calculateResult(context);
      LookupItemUtil.addLookupItem(set, object.toString(), "");
    }
    return set.toArray(new LookupItem[set.size()]);
  }

}
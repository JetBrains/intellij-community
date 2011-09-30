package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CollectionElementNameMacro extends Macro {
  public String getName() {
    return "collectionElementName";
  }

  public String getDescription() {
    return "collectionElementName()";
  }

  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    final Result paramResult = params[0].calculateResult(context);
    if (paramResult == null) {
      return null;
    }
    String param = paramResult.toString();
    int lastDot = param.lastIndexOf('.');
    if (lastDot >= 0) {
      param = param.substring(lastDot+1);
    }
    final String result = smartUnpluralize(param);
    return new TextResult(result);
  }

  private static String smartUnpluralize(String param) {
    if (param.endsWith("_list")) {
      return param.substring(0, param.length()-5);
    }
    final String result = StringUtil.unpluralize(param);
    return result == null ? param : result;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    Result result = calculateResult(params, context);
    if (result == null) {
      return null;
    }
    final String[] words = result.toString().split("_");
    if (words.length > 1) {
      List<LookupElement> lookup = new ArrayList<LookupElement>();
      for(int i=0; i<words.length; i++) {
        String element = StringUtil.join(words, i, words.length, "_");
        lookup.add(LookupElementBuilder.create(element));
      }
      return lookup.toArray(new LookupElement[lookup.size()]);
    }
    return null;
  }
}

package com.jetbrains.python.documentation;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class EpydocString extends StructuredDocString {
  public EpydocString(String docstringText) {
    super(docstringText, "@");
  }

  @Override
  @Nullable
  public String getReturnType() {
    String value = getTagValue("rtype");
    return removeInlineMarkup(value);
  }

  @Override
  @Nullable
  public String getParamType(String paramName) {
    String value = getTagValue("type", paramName);
    return removeInlineMarkup(value);
  }

  @Override
  @Nullable
  public String getParamDescription(String paramName) {
    String value = getTagValue("param", paramName);
    if (value == null) {
      value = getTagValue("param", "*" + paramName);
    }
    if (value == null) {
      value = getTagValue("param", "**" + paramName);
    }
    return value;
  }

  @Nullable
  public static String removeInlineMarkup(String s) {
    if (s == null) return null;
    StringBuilder resultBuilder = new StringBuilder();
    int pos = 0;
    while(true) {
      int bracePos = s.indexOf('{', pos);
      if (bracePos < 1) break;
      char prevChar = s.charAt(bracePos-1);
      if (prevChar >= 'A' && prevChar <= 'Z') {
        resultBuilder.append(s.substring(pos, bracePos-1));
        int rbracePos = s.indexOf('}', bracePos);
        if (rbracePos < 0) {
          pos = bracePos+1;
          break;
        }
        resultBuilder.append(s.substring(bracePos+1, rbracePos));
        pos = rbracePos+1;
      }
      else {
        resultBuilder.append(s.substring(pos, bracePos+1));
        pos = bracePos+1;
      }
    }
    resultBuilder.append(s.substring(pos));
    return resultBuilder.toString();
  }
}

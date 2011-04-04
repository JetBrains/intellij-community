package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class EpydocString {
  private final Map<String, String> mySimpleTagValues = Maps.newHashMap();
  private final Map<String, Map<String, String>> myArgTagValues = Maps.newHashMap();

  public EpydocString(String docstringText) {
    final String[] lines = LineTokenizer.tokenize(docstringText, false);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i].trim();
      if (line.startsWith("@")) {
        i = parseTag(lines, i);
      }
      i++;
    }
  }

  private int parseTag(String[] lines, int index) {
    String line = lines[index].trim();
    int tagEnd = StringUtil.indexOfAny(line, " \t:");
    if (tagEnd < 0) return index;
    String tagName = line.substring(1, tagEnd);
    String tagValue = line.substring(tagEnd).trim();
    int pos = tagValue.indexOf(':');
    if (pos < 0) return index;
    String value = tagValue.substring(pos+1).trim();
    while(index+1 < lines.length && !lines[index+1].trim().startsWith("@")) {
      index++;
      value += " " + lines[index].trim();
    }
    if (pos == 0) {
      mySimpleTagValues.put(tagName, value);
    }
    else {
      String arg = tagValue.substring(0, pos).trim();
      Map<String, String> argValues = myArgTagValues.get(arg);
      if (argValues == null) {
        argValues = Maps.newHashMap();
        myArgTagValues.put(tagName, argValues);
      }
      argValues.put(arg, value);
    }
    return index;
  }

  @Nullable
  public String getTagValue(String tagName) {
    return mySimpleTagValues.get(tagName);
  }

  @Nullable
  public String getTagValue(String tagName, String argName) {
    Map<String, String> argValues = myArgTagValues.get(tagName);
    return argValues == null ? null : argValues.get(argName);
  }

  @Nullable
  public String getReturnType() {
    String value = getTagValue("rtype");
    return removeInlineMarkup(value);
  }

  @Nullable
  public String getParamType(String paramName) {
    String value = getTagValue("type", paramName);
    return removeInlineMarkup(value);
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

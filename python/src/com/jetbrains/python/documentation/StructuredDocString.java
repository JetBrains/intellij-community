package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public abstract class StructuredDocString {
  protected final Map<String, String> mySimpleTagValues = Maps.newHashMap();
  protected final Map<String, Map<String, String>> myArgTagValues = Maps.newHashMap();

  public static StructuredDocString parse(String text) {
    if (text.contains(":param ") || text.contains(":rtype ") || text.contains(":type ")) {
      return new SphinxDocString(text);
    }
    return new EpydocString(text);
  }

  protected StructuredDocString(String docstringText, String tagPrefix) {
    final String[] lines = LineTokenizer.tokenize(docstringText, false);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i].trim();
      if (line.startsWith(tagPrefix)) {
        i = parseTag(lines, i, tagPrefix);
      }
      i++;
    }
  }

  private int parseTag(String[] lines, int index, String tagPrefix) {
    String line = lines[index].trim();
    int tagEnd = StringUtil.indexOfAny(line, " \t:", 1, line.length());
    if (tagEnd < 0) return index;
    String tagName = line.substring(1, tagEnd);
    String tagValue = line.substring(tagEnd).trim();
    int pos = tagValue.indexOf(':');
    if (pos < 0) return index;
    String value = tagValue.substring(pos+1).trim();
    while(index+1 < lines.length && !lines[index+1].trim().startsWith(tagPrefix)) {
      index++;
      value += " " + lines[index].trim();
    }
    if (pos == 0) {
      mySimpleTagValues.put(tagName, value);
    }
    else {
      String arg = tagValue.substring(0, pos).trim();
      Map<String, String> argValues = myArgTagValues.get(tagName);
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
  public abstract String getReturnType();

  @Nullable
  public abstract String getParamType(String paramName);

  @Nullable
  public abstract String getParamDescription(String paramName);
}

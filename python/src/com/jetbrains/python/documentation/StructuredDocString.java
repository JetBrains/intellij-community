package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class StructuredDocString {
  protected final String myDescription;
  protected final Map<String, String> mySimpleTagValues = Maps.newHashMap();
  protected final Map<String, Map<String, String>> myArgTagValues = Maps.newHashMap();

  @Nullable
  public static StructuredDocString parse(String text) {
    if (text == null) {
      return null;
    }
    if (text.contains(":param ") || text.contains(":rtype ") || text.contains(":type ")) {
      return new SphinxDocString(text);
    }
    return new EpydocString(text);
  }

  protected StructuredDocString(String docstringText, String tagPrefix) {
    final String[] lines = LineTokenizer.tokenize(docstringText, false);
    int i = 0;
    StringBuilder descBuilder = new StringBuilder();
    while (i < lines.length) {
      String line = lines[i].trim();
      if (line.startsWith(tagPrefix)) {
        i = parseTag(lines, i, tagPrefix);
      }
      else {
        descBuilder.append(line).append("\n");
      }
      i++;
    }
    myDescription = descBuilder.toString();
  }

  public String getDescription() {
    return myDescription;
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
        argValues = Maps.newLinkedHashMap();
        myArgTagValues.put(tagName, argValues);
      }
      argValues.put(arg, value);
    }
    return index;
  }

  @Nullable
  public String getTagValue(String... tagNames) {
    for (String tagName : tagNames) {
      final String value = mySimpleTagValues.get(tagName);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @Nullable
  public String getTagValue(String tagName, String argName) {
    Map<String, String> argValues = myArgTagValues.get(tagName);
    return argValues == null ? null : argValues.get(argName);
  }

  @Nullable
  public String getTagValue(String[] tagNames, String argName) {
    for (String tagName : tagNames) {
      Map<String, String> argValues = myArgTagValues.get(tagName);
      if (argValues != null) {
        return argValues.get(argName);
      }
    }
    return null;
  }

  public List<String> getTagArguments(String... tagNames) {
    for (String tagName : tagNames) {
      final Map<String, String> map = myArgTagValues.get(tagName);
      if (map != null) {
        return new ArrayList<String>(map.keySet());
      }
    }
    return Collections.emptyList();
  }

  public abstract List<String> getParameters();
  public abstract List<String> getKeywordArguments();

  @Nullable
  public abstract String getReturnType();

  @Nullable
  public abstract String getReturnDescription();

  @Nullable
  public abstract String getParamType(@Nullable String paramName);

  @Nullable
  public abstract String getParamDescription(String paramName);
  public abstract String getKeywordArgumentDescription(String paramName);

  public abstract List<String> getRaisedExceptions();

  public abstract String getRaisedExceptionDescription(String exceptionName);
}

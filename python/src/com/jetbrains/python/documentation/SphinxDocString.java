package com.jetbrains.python.documentation;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class SphinxDocString extends StructuredDocString {
  public static String[] KEYWORD_ARGUMENT_TAGS = new String[] { "keyword", "key" };
  public static String[] ALL_TAGS = new String[] { ":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
                                                   ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
                                                   ":return", ":returns", ":rtype", ":except", ":exception" };

  public SphinxDocString(String docstringText) {
    super(docstringText, ":");
  }

  @Override
  public List<String> getParameters() {
    return getTagArguments(EpydocString.PARAM_TAGS);
  }

  @Override
  public List<String> getKeywordArguments() {
    return getTagArguments(KEYWORD_ARGUMENT_TAGS);
  }

  @Override
  public String getKeywordArgumentDescription(String paramName) {
    return getTagValue(KEYWORD_ARGUMENT_TAGS, paramName);
  }

  @Override
  public String getReturnType() {
    return getTagValue("rtype");
  }

  @Override
  public String getParamType(@Nullable String paramName) {
    return paramName == null ? getTagValue("type") : getTagValue("type", paramName);
  }

  @Override
  public String getParamDescription(String paramName) {
    return getTagValue("param", paramName);
  }

  @Override
  public String getReturnDescription() {
    return getTagValue(EpydocString.RETURN_TAGS);
  }

  @Override
  public List<String> getRaisedExceptions() {
    return getTagArguments(EpydocString.RAISES_TAGS);
  }

  @Override
  public String getRaisedExceptionDescription(String exceptionName) {
    return getTagValue(EpydocString.RAISES_TAGS, exceptionName);
  }

  @Override
  public String getAttributeDescription() {
    return getTagValue(EpydocString.VARIABLE_TAGS);
  }

  @Override
  public List<String> getAdditionalTags() {
    return Collections.emptyList();
  }

  protected int parseTag(String[] lines, int index, String tagPrefix) {
    String line = lines[index].trim();
    int tagEnd = StringUtil.indexOfAny(line, " \t:", 1, line.length());
    if (tagEnd < 0) return index;
    String tagName = line.substring(1, tagEnd);
    String tagValue = line.substring(tagEnd).trim();
    tagValue = StringUtil.replace(tagValue, ":py:class:", "");
    tagValue = StringUtil.replace(tagValue, ":class:", "");
    tagValue = tagValue.replaceAll("`(\\w+)`", "$1");
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
      if ("param".equals(tagName) || "parameter".equals(tagName) ||
        "arg".equals(tagName) || "argument".equals(tagName)) {
        int ws = CharMatcher.anyOf(" \t*").indexIn(tagValue, 1);
        if (ws != -1) {
          int next = CharMatcher.anyOf(" \t*").negate().indexIn(tagValue, ws);
          if (next != -1 && !tagValue.substring(0, next).contains(":")) {
            Map<String, String> argValues = myArgTagValues.get("type");
            if (argValues == null) {
              argValues = Maps.newLinkedHashMap();
              myArgTagValues.put("type", argValues);
            }
            CharMatcher identifierMatcher = new CharMatcher() {
                                        @Override public boolean matches(char c) {
                                          return Character.isLetterOrDigit(c) || c == '_' || c == '.';
                                        }}.negate();
            int endType = identifierMatcher.indexIn(tagValue, 0);
            int endArg = tagValue.indexOf(':');
            String arg = tagValue.substring(endType, endArg).trim();
            argValues.put(arg, tagValue.substring(0, endType).trim());
            argValues = myArgTagValues.get(tagName);
            if (argValues == null) {
              argValues = Maps.newLinkedHashMap();
              myArgTagValues.put(tagName, argValues);
            }
            argValues.put(arg, value);
            return index;
          }
        }
      }
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
}

package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    if (line.startsWith(tagPrefix)) {
      line = line.substring(tagPrefix.length());
      final Pattern tagPattern = Pattern.compile("([a-z]+)(.*):([^:]*)");
      final Matcher tagMatcher = tagPattern.matcher(line);
      if (tagMatcher.matches()) {
        final String tagName = tagMatcher.group(1);
        final String argName = tagMatcher.group(2).trim();
        final StringBuilder builder = new StringBuilder();
        builder.append(tagMatcher.group(3).trim());
        for (index += 1; index < lines.length && !lines[index].trim().startsWith(tagPrefix); index++) {
          builder.append(" ");
          builder.append(lines[index].trim());
        }
        index--;
        final String argValue = builder.toString().trim();
        if (argName.isEmpty()) {
          mySimpleTagValues.put(tagName, argValue);
        }
        else {
          if ("param".equals(tagName) || "parameter".equals(tagName) ||
              "arg".equals(tagName) || "argument".equals(tagName)) {
            final Pattern argPattern = Pattern.compile("(.*) ([a-zA-Z_0-9]+)");
            final Matcher argMatcher = argPattern.matcher(argName);
            if (argMatcher.matches()) {
              final String type = argMatcher.group(1).trim();
              final String arg = argMatcher.group(2);
              getTagValuesMap("type").put(arg, type);
              getTagValuesMap(tagName).put(arg, argValue);
            }
          }
          else {
            getTagValuesMap(tagName).put(argName, argValue);
          }
        }
      }
    }
    return index;
  }

  @NotNull
  private Map<String, String> getTagValuesMap(String key) {
    Map<String, String> map = myArgTagValues.get(key);
    if (map == null) {
      map = Maps.newLinkedHashMap();
      myArgTagValues.put(key, map);
    }
    return map;
  }
}

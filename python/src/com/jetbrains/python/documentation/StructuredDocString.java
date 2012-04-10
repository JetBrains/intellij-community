package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class StructuredDocString {
  protected final String myDescription;

  protected final Map<String, Substring> mySimpleTagValues = Maps.newHashMap();
  protected final Map<String, Map<Substring, Substring>> myArgTagValues = Maps.newHashMap();

  private static final Pattern RE_STRICT_TAG_LINE = Pattern.compile("([a-z]+)(.*): (.*)");
  private static final Pattern RE_LOOSE_TAG_LINE = Pattern.compile("([a-z]+) ([a-zA-Z_0-9]*):?([^:]*)");
  private static final Pattern RE_ARG_TYPE = Pattern.compile("(.*) ([a-zA-Z_0-9]+)");

  public static String[] PARAM_TAGS = new String[] { "param", "parameter", "arg", "argument" };
  public static String[] PARAM_TYPE_TAGS = new String[] { "type" };

  @Nullable
  public static StructuredDocString parse(String text) {
    if (text == null) {
      return null;
    }
    if (text.contains(":param ") || text.contains(":rtype") || text.contains(":type")) {
      return new SphinxDocString(text);
    }
    return new EpydocString(text);
  }

  protected StructuredDocString(@NotNull String docStringText, String tagPrefix) {
    final Substring docString = new Substring(docStringText);
    final List<Substring> lines = docString.splitLines();
    final int nlines = lines.size();
    final StringBuilder builder = new StringBuilder();
    int lineno = 0;
    while (lineno < nlines) {
      Substring line = lines.get(lineno).trim();
      if (line.startsWith(tagPrefix)) {
        lineno = parseTag(lines, lineno, tagPrefix);
      }
      else {
        builder.append(line.toString()).append("\n");
      }
      lineno++;
    }
    myDescription = builder.toString();
  }

  public String getDescription() {
    return myDescription;
  }

  @NotNull
  private Map<Substring, Substring> getTagValuesMap(String key) {
    Map<Substring, Substring> map = myArgTagValues.get(key);
    if (map == null) {
      map = Maps.newLinkedHashMap();
      myArgTagValues.put(key, map);
    }
    return map;
  }

  protected int parseTag(List<Substring> lines, int lineno, String tagPrefix) {
    final Substring lineWithPrefix = lines.get(lineno).trim();
    if (lineWithPrefix.startsWith(tagPrefix)) {
      final Substring line = lineWithPrefix.substring(tagPrefix.length());
      final Matcher strictTagMatcher = RE_STRICT_TAG_LINE.matcher(line);
      final Matcher looseTagMatcher = RE_LOOSE_TAG_LINE.matcher(line);
      Matcher tagMatcher = null;
      if (strictTagMatcher.matches()) {
        tagMatcher = strictTagMatcher;
      }
      else if (looseTagMatcher.matches()) {
        tagMatcher = looseTagMatcher;
      }
      if (tagMatcher != null) {
        final Substring tagName = line.getMatcherGroup(tagMatcher, 1);
        final Substring argName = line.getMatcherGroup(tagMatcher, 2).trim();
        final TextRange firstArgLineRange = line.getMatcherGroup(tagMatcher, 3).trim().getTextRange();
        final int linesCount = lines.size();
        final int argStart = firstArgLineRange.getStartOffset();
        int argEnd = firstArgLineRange.getEndOffset();
        while (lineno + 1 < linesCount) {
          final Substring nextLine = lines.get(lineno + 1).trim();
          if (nextLine.length() == 0 || nextLine.startsWith(tagPrefix)) {
            break;
          }
          argEnd = nextLine.getTextRange().getEndOffset();
          lineno++;
        }
        final Substring argValue = new Substring(argName.getSuperString(), argStart, argEnd);
        final String tagNameString = tagName.toString();
        if (argName.length() == 0) {
          mySimpleTagValues.put(tagNameString, argValue);
        }
        else {
          if ("param".equals(tagNameString) || "parameter".equals(tagNameString) ||
              "arg".equals(tagNameString) || "argument".equals(tagNameString)) {
            final Matcher argTypeMatcher = RE_ARG_TYPE.matcher(argName);
            if (argTypeMatcher.matches()) {
              final Substring type = argName.getMatcherGroup(argTypeMatcher, 1).trim();
              final Substring arg = argName.getMatcherGroup(argTypeMatcher, 2);
              getTagValuesMap("type").put(arg, type);
              getTagValuesMap(tagNameString).put(arg, argValue);
            }
            else {
              getTagValuesMap(tagNameString).put(argName, argValue);
            }
          }
          else {
            getTagValuesMap(tagNameString).put(argName, argValue);
          }
        }
      }
    }
    return lineno;
  }

  protected static List<String> toUniqueStrings(List<?> objects) {
    final List<String> result = new ArrayList<String>(objects.size());
    for (Object o : objects) {
      final String s = o.toString();
      if (!result.contains(s)) {
        result.add(s);
      }
    }
    return result;
  }

  @Nullable
  public Substring getTagValue(String... tagNames) {
    for (String tagName : tagNames) {
      final Substring value = mySimpleTagValues.get(tagName);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @Nullable
  public Substring getTagValue(String tagName, @NotNull String argName) {
    final Map<Substring, Substring> argValues = myArgTagValues.get(tagName);
    return argValues != null ? argValues.get(new Substring(argName)) : null;
  }

  @Nullable
  public Substring getTagValue(String[] tagNames, @NotNull String argName) {
    for (String tagName : tagNames) {
      Map<Substring, Substring> argValues = myArgTagValues.get(tagName);
      if (argValues != null) {
        return argValues.get(new Substring(argName));
      }
    }
    return null;
  }

  public List<Substring> getTagArguments(String... tagNames) {
    for (String tagName : tagNames) {
      final Map<Substring, Substring> map = myArgTagValues.get(tagName);
      if (map != null) {
        return new ArrayList<Substring>(map.keySet());
      }
    }
    return Collections.emptyList();
  }

  public List<Substring> getParameterSubstrings() {
    final List<Substring> results = new ArrayList<Substring>();
    results.addAll(getTagArguments(PARAM_TAGS));
    results.addAll(getTagArguments(PARAM_TYPE_TAGS));
    return results;
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
  public abstract String getParamDescription(@Nullable String paramName);

  @Nullable
  public abstract String getKeywordArgumentDescription(@Nullable String paramName);

  public abstract List<String> getRaisedExceptions();

  @Nullable
  public abstract String getRaisedExceptionDescription(@Nullable String exceptionName);

  @Nullable
  public abstract String getAttributeDescription();
  public abstract List<String> getAdditionalTags();

  public abstract List<Substring> getKeywordArgumentSubstrings();

  @Nullable
  public abstract Substring getReturnTypeSubstring();

  @Nullable
  abstract Substring getParamTypeSubstring(@Nullable String paramName);
}

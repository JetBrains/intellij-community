package com.jetbrains.python.documentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class SphinxDocString extends StructuredDocString {
  public static String[] KEYWORD_ARGUMENT_TAGS = new String[] { "keyword", "key" };
  public static String[] ALL_TAGS = new String[] { ":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
                                                   ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
                                                   ":return", ":returns", ":rtype", ":except", ":exception" };

  public SphinxDocString(@NotNull String docstringText) {
    super(docstringText, ":");
  }

  @Nullable
  protected static String concatTrimmedLines(@Nullable Substring s) {
    return s != null ? s.concatTrimmedLines(" ") : null;
  }

  @Override
  public List<String> getParameters() {
    return toUniqueStrings(getParameterSubstrings());
  }

  @Override
  public List<String> getKeywordArguments() {
    return toUniqueStrings(getKeywordArgumentSubstrings());
  }

  @Nullable
  @Override
  public String getKeywordArgumentDescription(@Nullable String paramName) {
    if (paramName == null) {
      return null;
    }
    return concatTrimmedLines(getTagValue(KEYWORD_ARGUMENT_TAGS, paramName));
  }

  @Override
  public String getReturnType() {
    return concatTrimmedLines(getReturnTypeSubstring());
  }

  @Override
  public String getParamType(@Nullable String paramName) {
    return concatTrimmedLines(getParamTypeSubstring(paramName));
  }

  @Nullable
  @Override
  public String getParamDescription(@Nullable String paramName) {
    return paramName != null ? concatTrimmedLines(getTagValue("param", paramName)) : null;
  }

  @Override
  public String getReturnDescription() {
    return concatTrimmedLines(getTagValue(EpydocString.RETURN_TAGS));
  }

  @Override
  public List<String> getRaisedExceptions() {
    return toUniqueStrings(getTagArguments(EpydocString.RAISES_TAGS));
  }

  @Nullable
  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName == null) {
      return null;
    }
    return concatTrimmedLines(getTagValue(EpydocString.RAISES_TAGS, exceptionName));
  }

  @Override
  public String getAttributeDescription() {
    return concatTrimmedLines(getTagValue(EpydocString.VARIABLE_TAGS));
  }

  @Override
  public List<String> getAdditionalTags() {
    return Collections.emptyList();
  }

  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return getTagArguments(KEYWORD_ARGUMENT_TAGS);
  }

  @Override
  public Substring getReturnTypeSubstring() {
    return getTagValue("rtype");
  }

  @Override
  Substring getParamTypeSubstring(@Nullable String paramName) {
    return paramName == null ? getTagValue("type") : getTagValue("type", paramName);
  }
}

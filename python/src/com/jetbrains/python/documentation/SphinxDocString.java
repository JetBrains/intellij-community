package com.jetbrains.python.documentation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

  public SphinxDocString(String docstringText) {
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

  @Override
  public String getKeywordArgumentDescription(String paramName) {
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

  @Override
  public String getParamDescription(String paramName) {
    return concatTrimmedLines(getTagValue("param", paramName));
  }

  @Override
  public String getReturnDescription() {
    return concatTrimmedLines(getTagValue(EpydocString.RETURN_TAGS));
  }

  @Override
  public List<String> getRaisedExceptions() {
    return toUniqueStrings(getTagArguments(EpydocString.RAISES_TAGS));
  }

  @Override
  public String getRaisedExceptionDescription(String exceptionName) {
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
  public List<Substring> getParameterSubstrings() {
    final List<Substring> results = new ArrayList<Substring>();
    results.addAll(getTagArguments(EpydocString.PARAM_TAGS));
    results.addAll(getTagArguments(EpydocString.PARAM_TYPE_TAGS));
    return results;
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

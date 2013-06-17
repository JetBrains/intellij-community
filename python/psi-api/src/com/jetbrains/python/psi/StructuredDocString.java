package com.jetbrains.python.psi;

import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public interface StructuredDocString {
  String getDescription();

  String getSummary();

  @Nullable
  Substring getTagValue(String... tagNames);

  @Nullable
  Substring getTagValue(String tagName, @NotNull String argName);

  @Nullable
  Substring getTagValue(String[] tagNames, @NotNull String argName);

  List<Substring> getTagArguments(String... tagNames);

  List<Substring> getParameterSubstrings();

  @Nullable
  Substring getParamByNameAndKind(@NotNull String name, String kind);

  List<String> getParameters();

  List<String> getKeywordArguments();

  @Nullable
  String getReturnType();

  @Nullable
  String getReturnDescription();

  @Nullable
  String getParamType(@Nullable String paramName);

  @Nullable
  String getParamDescription(@Nullable String paramName);

  @Nullable
  String getKeywordArgumentDescription(@Nullable String paramName);

  List<String> getRaisedExceptions();

  @Nullable
  String getRaisedExceptionDescription(@Nullable String exceptionName);

  @Nullable
  String getAttributeDescription();

  List<String> getAdditionalTags();

  List<Substring> getKeywordArgumentSubstrings();

  @Nullable
  Substring getReturnTypeSubstring();

  @Nullable
  Substring getParamTypeSubstring(@Nullable String paramName);
}

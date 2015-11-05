/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.docstrings;

import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class SphinxDocString extends TagBasedDocString {
  public static String[] KEYWORD_ARGUMENT_TAGS = new String[] { "keyword", "key" };
  public static String[] ALL_TAGS = new String[] { ":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
                                                   ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
                                                   ":return", ":returns", ":rtype", ":except", ":exception" };

  public SphinxDocString(@NotNull final Substring docstringText) {
    super(docstringText, ":");
  }

  @Nullable
  protected static String concatTrimmedLines(@Nullable Substring s) {
    return s != null ? s.concatTrimmedLines(" ") : null;
  }

  @NotNull
  @Override
  public List<String> getParameters() {
    return toUniqueStrings(getParameterSubstrings());
  }

  @NotNull
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
    return concatTrimmedLines(getTagValue(RETURN_TAGS));
  }

  @NotNull
  @Override
  public List<String> getRaisedExceptions() {
    return toUniqueStrings(getTagArguments(RAISES_TAGS));
  }

  @Nullable
  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName == null) {
      return null;
    }
    return concatTrimmedLines(getTagValue(RAISES_TAGS, exceptionName));
  }

  @Override
  public String getAttributeDescription() {
    return concatTrimmedLines(getTagValue(VARIABLE_TAGS));
  }

  @Override
  public List<String> getAdditionalTags() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return getTagArguments(KEYWORD_ARGUMENT_TAGS);
  }

  @Override
  public Substring getReturnTypeSubstring() {
    return getTagValue("rtype");
  }

  @Override
  public Substring getParamTypeSubstring(@Nullable String paramName) {
    return paramName == null ? getTagValue("type") : getTagValue("type", paramName);
  }

  @NotNull
  @Override
  public String getDescription() {
    return myDescription.replaceAll("\n", "<br/>");
  }
}

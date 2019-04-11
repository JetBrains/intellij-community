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

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author yole
 */
public class SphinxDocString extends TagBasedDocString {
  public static String[] KEYWORD_ARGUMENT_TAGS = new String[] { "keyword", "key" };
  public static String[] ALL_TAGS = new String[] { ":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
                                                   ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
                                                   ":return", ":returns", ":rtype", ":except", ":exception", ":py:meth" };
  public static final Map<String, DocStringParameterReference.ReferenceType> tag2RefType;
  public static final Map<Pair<String, Pattern>, DocStringParameterReference.ReferenceType> tagPattern2RefType;
  public static final String METHOD = "method";

  static {
    tag2RefType = new HashMap<>();
    tag2RefType.put(":py:meth", DocStringParameterReference.ReferenceType.METHOD);

    tagPattern2RefType = tag2RefType.entrySet().stream().map(kv ->
      new Pair<>(
        new Pair<>(
          kv.getKey(),
          Pattern.compile("(" + kv.getKey() + ":`[^`]*`)")
        ),
        kv.getValue())
    ).collect(Collectors.toMap(kv -> kv.getFirst(), kv -> kv.getSecond()));
  }

  public SphinxDocString(@NotNull final Substring docstringText) {
    super(docstringText, ":");
  }

  @Override
  protected void processTagValues() {
    super.processTagValues();

    List<Substring> allSubstrs = new ArrayList<>();
    allSubstrs.addAll(mySimpleTagValues.values());
    allSubstrs.addAll(myArgTagValues.entrySet().stream().flatMap(kv -> kv.getValue().values().stream()).collect(Collectors.toList()));

    for (Substring value : allSubstrs) {
      final String valStr = value.toString();

      for (Pair<String, Pattern> tagPattern : tagPattern2RefType.keySet()) {
        String tag = tagPattern.getFirst();
        Matcher matcher = tagPattern.getSecond().matcher(valStr);
        while (matcher.find()) {
          // TODO: add correct position
          int offset = tag.length() + 2 + matcher.start();

          // TODO: check if null is ok
          getTagValuesMap(METHOD).put(new Substring(value.getSuperString(), value.getStartOffset() + offset, value.getStartOffset() + matcher.end() - 1), null);
        }
      }
    }
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
    return paramName != null ? concatTrimmedLines(getTagValue(PARAM_TAGS, paramName)) : null;
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

  @Override
  public String[] getMethodReferenceTags() {
    return new String[] {METHOD};
  }
}

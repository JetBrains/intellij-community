/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Experimental
public class YamlEnumType extends YamlScalarType {
  private String[] myLiterals = ArrayUtil.EMPTY_STRING_ARRAY;
  private String[] myHiddenLiterals = ArrayUtil.EMPTY_STRING_ARRAY;
  private String[] myDeprecatedLiterals = ArrayUtil.EMPTY_STRING_ARRAY;


  public YamlEnumType(@NotNull String typeName) {
    super(typeName);
  }

  @NotNull
  public YamlEnumType withLiterals(String... literals) {
    myLiterals = cloneArray(literals);
    return this;
  }

  /**
   * Specifies subset of the valid values which will be accepted by validation but will NOT be offered in
   * completion lists. These values must not overlap with the visible (see {@link #withLiterals(String...)}),
   * but may overlap with the deprecated (see {@link #withDeprecatedLiterals(String...)})
   */
  @NotNull
  public YamlEnumType withHiddenLiterals(String... hiddenLiterals) {
    myHiddenLiterals = hiddenLiterals.clone();
    return this;
  }

  /**
   * Specifies subset of the valid values which will be considered deprecated.
   * They will be available in completion list (struck out) and highlighted after validation.
   * These values must not overlap with the visible (see {@link #withLiterals(String...)}),
   * but may overlap with the hidden (see {@link #withHiddenLiterals(String...)})
   */
  @NotNull
  public YamlEnumType withDeprecatedLiterals(String... deprecatedLiterals) {
    myDeprecatedLiterals = deprecatedLiterals.clone();
    return this;
  }

  @NotNull
  protected final Stream<String> getLiteralsStream() {
    return Stream.concat(Arrays.stream(myLiterals), Arrays.stream(myDeprecatedLiterals));
  }

  @Override
  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    super.validateScalarValue(scalarValue, holder);

    String text = scalarValue.getTextValue().trim();
    if (text.length() == 0) {
      // not our business
      return;
    }

    if (Arrays.stream(myDeprecatedLiterals).anyMatch(text::equals)) {
      holder.registerProblem(scalarValue,
                             YAMLBundle.message("YamlEnumType.validation.warning.value.deprecated", text),
                             ProblemHighlightType.LIKE_DEPRECATED);
    }
    else if (Stream.concat(Arrays.stream(myHiddenLiterals), getLiteralsStream()).noneMatch(text::equals)) {
      //TODO quickfix makes sense here if !text.equals(text.toLowerCase)
      holder.registerProblem(scalarValue,
                             YAMLBundle.message("YamlEnumType.validation.error.value.unknown", text),
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
  }

  @NotNull
  @Override
  public List<LookupElement> getValueLookups(@NotNull YAMLScalar context) {
    return Stream.concat(
      Arrays.stream(myLiterals).map((String literal) -> createValueLookup(literal, false)),
      Arrays.stream(myDeprecatedLiterals).map((String literal) -> createValueLookup(literal, true))
    )
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Nullable
  protected LookupElement createValueLookup(@NotNull String literal, boolean deprecated) {
    return LookupElementBuilder.create(literal).withStrikeoutness(deprecated);
  }


  @NotNull
  private static String[] cloneArray(@NotNull String[] array) {
    return array.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : array.clone();
  }
}

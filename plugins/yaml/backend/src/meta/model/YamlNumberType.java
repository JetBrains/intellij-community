/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.regex.Pattern;

@ApiStatus.Internal
public class YamlNumberType extends YamlScalarType {
  private static final YamlNumberType SHARED_INSTANCE_NO_QUOTED_VALUES_ALLOWED = new YamlNumberType(false);
  private static final YamlNumberType SHARED_INSTANCE_QUOTED_VALUES_ALLOWED = new YamlNumberType(true);

  /**
   * Recognizes the integer forms allowed by the YAML 1.1 spec (plus comma-grouped decimals): base 2, base 8, base 10,
   * base 16, and sexagesimal (base 60) literals, with an optional sign.
   */
  private static final Pattern INT_PATTERN = Pattern.compile(
    "[-+]?0b[0-1_]+"                          // base 2
    + "|[-+]?0[0-7_]+"                        // base 8
    + "|[-+]?(0|[1-9][0-9_]*)"                // base 10
    + "|[-+]?0x[0-9a-fA-F_]+"                 // base 16
    + "|[-+]?[1-9][0-9_]*(:[0-5]?[0-9])+"     // base 60 (sexagesimal)
    + "|[-+]?[0-9]+(,[0-9]+)+"                // comma-grouped decimal
  );

  private final boolean myQuotedValuesAllowed;

  public static YamlNumberType getInstance(boolean quotedValuesAllowed) {
    return quotedValuesAllowed ? SHARED_INSTANCE_QUOTED_VALUES_ALLOWED : SHARED_INSTANCE_NO_QUOTED_VALUES_ALLOWED;
  }

  public YamlNumberType(boolean quotedValuesAllowed) {
    super("yaml:number", "number");
    myQuotedValuesAllowed = quotedValuesAllowed;
  }

  @Override
  public boolean isSupportedTag(@NotNull String tag) {
    return tag.contains("int") || tag.contains("float") || tag.contains("double") || tag.contains("number");
  }

  @Override
  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    if (!myQuotedValuesAllowed && scalarValue instanceof YAMLQuotedText) {
      holder.registerProblem(scalarValue, YAMLBundle.message("YamlNumberType.error.numeric.value"), ProblemHighlightType.ERROR);
      return;
    }

    // An explicit numeric tag (e.g. !!int, !!float) forces the numeric type regardless of the lexical representation of
    // the value, mirroring the way an explicit !!str tag forces the string type.
    PsiElement tag = scalarValue.getTag();
    if (tag != null && isSupportedTag(tag.getText())) {
      return;
    }

    if (!isNumericTextValue(scalarValue.getTextValue())) {
      holder.registerProblem(scalarValue, YAMLBundle.message("YamlNumberType.error.numeric.value"), ProblemHighlightType.ERROR);
    }
  }

  private static boolean isNumericTextValue(@NotNull String textValue) {
    // Float.parseFloat() successfully parses values like " 1.0 ", i.e. starting or ending with spaces,
    // which is not valid for a typed schema
    if (textValue.startsWith(" ") || textValue.endsWith(" ")) {
      return false;
    }

    // Non-decimal integer literals (hexadecimal, octal, binary, sexagesimal, comma-grouped) are valid YAML integers,
    // but are not recognized by Float.parseFloat().
    if (INT_PATTERN.matcher(textValue).matches()) {
      return true;
    }

    try {
      Float.parseFloat(textValue);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }
}

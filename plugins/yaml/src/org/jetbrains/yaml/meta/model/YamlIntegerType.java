/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;

@ApiStatus.Internal
public class YamlIntegerType extends YamlScalarType {
  private static final YamlIntegerType SHARED_INSTANCE_NO_QUOTED_VALUES_ALLOWED = new YamlIntegerType(false);
  private static final YamlIntegerType SHARED_INSTANCE_QUOTED_VALUES_ALLOWED = new YamlIntegerType(true);

  private final boolean myQuotedValuesAllowed;

  public static YamlIntegerType getInstance(boolean quotedValuesAllowed) {
    return quotedValuesAllowed ? SHARED_INSTANCE_QUOTED_VALUES_ALLOWED : SHARED_INSTANCE_NO_QUOTED_VALUES_ALLOWED;
  }

  public YamlIntegerType(boolean quotedValuesAllowed) {
    super("yaml:integer");
    myQuotedValuesAllowed = quotedValuesAllowed;
    setDisplayName("integer");
  }

  @Override
  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    try {
      if (!myQuotedValuesAllowed && scalarValue instanceof YAMLQuotedText) {
        throw new NumberFormatException("no quoted values allowed");
      }
      Long.parseLong(scalarValue.getTextValue());
    }
    catch (NumberFormatException e) {
      holder.registerProblem(scalarValue, YAMLBundle.message("YamlIntegerType.error.integer.value"), ProblemHighlightType.ERROR);
    }
  }
}

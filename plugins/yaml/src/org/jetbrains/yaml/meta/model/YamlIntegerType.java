/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLScalar;

@ApiStatus.Experimental
public class YamlIntegerType extends YamlScalarType {
  private static final YamlIntegerType SHARED_INSTANCE = new YamlIntegerType();

  public static YamlIntegerType getInstance() {
    return SHARED_INSTANCE;
  }

  public YamlIntegerType() {
    super("yaml:integer");
    setDisplayName("integer");
  }

  @Override
  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    try {
      //noinspection ResultOfMethodCallIgnored
      Integer.parseInt(scalarValue.getTextValue());
    }
    catch (NumberFormatException e) {
      holder.registerProblem(scalarValue, YAMLBundle.message("YamlIntegerType.error.integer.value"), ProblemHighlightType.ERROR);
    }
  }
}

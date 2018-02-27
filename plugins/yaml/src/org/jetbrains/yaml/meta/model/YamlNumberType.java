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
public class YamlNumberType extends YamlScalarType {
  private static final YamlNumberType SHARED_INSTANCE = new YamlNumberType();

  public static YamlNumberType getInstance() {
    return SHARED_INSTANCE;
  }

  public YamlNumberType() {
    super("yaml:number");
    setDisplayName("number");
  }

  @Override
  protected void validateScalarValue(@NotNull YAMLScalar scalarValue, @NotNull ProblemsHolder holder) {
    try {
      final String textValue = scalarValue.getTextValue();

      // Float.parseFloat() successfully parses values like " 1.0 ", i.e. starting or ending with spaces,
      // which is not valid for typed schema
      if (textValue.startsWith(" ") || textValue.endsWith(" ")) {
        throw new NumberFormatException("contains spaces");
      }
      //noinspection ResultOfMethodCallIgnored
      Float.parseFloat(textValue);
    }
    catch (NumberFormatException e) {
      holder.registerProblem(scalarValue, YAMLBundle.message("YamlNumberType.error.numeric.value"), ProblemHighlightType.ERROR);
    }
  }
}

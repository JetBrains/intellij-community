// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.regex.Pattern;

public final class YamlKeyValueRenameInputValidator implements RenameInputValidatorEx {
  private static final String IDENTIFIER_START_PATTERN = "(([^\\n\\t\\r \\-?:,\\[\\]{}#&*!|>'\"%@`])" +
                                                         "|([?:-][^\\n\\t\\r ])" +
                                                         ")";

  private static final String IDENTIFIER_END_PATTERN = "(([^\\n\\t\\r ]#)" +
                                                       "|([^\\n\\t\\r :#])" +
                                                       "|(:[^\\n\\t\\r ])" +
                                                       ")";

  // Taken from yaml.flex, NS_PLAIN_ONE_LINE_block. This may not be entirely correct, but it is less restrictive than the default names
  // validator
  public static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
    "(" + IDENTIFIER_START_PATTERN + "([ \t]*" + IDENTIFIER_END_PATTERN + ")*)|" +
    "('[^\\n']*')|(\"[^\\n\"]*\")"
  );

  @Override
  public @Nullable String getErrorMessage(final @NotNull String newName, final @NotNull Project project) {
    return IDENTIFIER_PATTERN.matcher(newName).matches() ? null : YAMLBundle.message("rename.invalid.name", newName);
  }

  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiElement(YAMLKeyValue.class);
  }

  @Override
  public boolean isInputValid(final @NotNull String newName, final @NotNull PsiElement element, final @NotNull ProcessingContext context) {
    return true;
  }
}

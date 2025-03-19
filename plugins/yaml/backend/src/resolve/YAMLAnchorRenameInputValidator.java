// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.resolve;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAnchor;

import static com.intellij.patterns.PlatformPatterns.psiElement;

final class YAMLAnchorRenameInputValidator implements RenameInputValidator {
  @Override
  public @NotNull ElementPattern<? extends PsiElement> getPattern() {
    return psiElement(YAMLAnchor.class);
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return newName.matches("[^,\\[\\]{}\\n\\t ]+");
  }
}

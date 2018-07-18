// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.resolve;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLAnchor;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class YAMLAnchorRenameInputValidator implements RenameInputValidator {
  @NotNull
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return psiElement(YAMLAnchor.class);
  }

  @Override
  public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
    return newName.matches("[^,\\[\\]{}\\n\\t ]+");
  }
}

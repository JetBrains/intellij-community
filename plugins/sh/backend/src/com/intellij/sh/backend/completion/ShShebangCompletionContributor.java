// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.sh.ShTypes.SHEBANG;

public class ShShebangCompletionContributor extends CompletionContributor implements DumbAware {
  private static final String SHEBANG_PREFIX = "#!";
  private static final @NonNls List<String> ACCEPTABLE_SHELLS = Arrays.asList("/usr/bin/env bash",
                                                                              "/usr/bin/env sh",
                                                                              "/usr/bin/env zsh",
                                                                              "/usr/bin/env csh",
                                                                              "/usr/bin/env ksh",
                                                                              "/usr/bin/env tcsh");

  private static final @NonNls CompletionProvider<CompletionParameters> SHEBANG_COMPLETION_PROVIDER = new CompletionProvider<>() {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      PsiElement original = parameters.getOriginalPosition();
      if (original == null) return;
      int textLength = parameters.getOffset() - parameters.getPosition().getTextRange().getStartOffset();
      String originalText = original.getText().substring(0, textLength);
      CompletionResultSet resultSet = result.withPrefixMatcher(originalText);

      String defaultShell = EnvironmentUtil.getValue("SHELL");
      if (defaultShell != null) {
        String defaultShebang = SHEBANG_PREFIX + defaultShell;
        if (defaultShebang.startsWith(originalText)) {
          resultSet.addElement(createLookupElement(defaultShebang, parameters, 10));
        }
      }

      ACCEPTABLE_SHELLS.stream().map(shell -> SHEBANG_PREFIX + shell)
        .filter(shebang -> shebang.startsWith(originalText))
        .forEach(shebang -> resultSet.addElement(createLookupElement(shebang, parameters, 0)));
      resultSet.stopHere();
    }
  };

  public ShShebangCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withElementType(SHEBANG), SHEBANG_COMPLETION_PROVIDER);
  }

  private static LookupElement createLookupElement(@NotNull String lookupString, @NotNull CompletionParameters parameters, int priority) {
    return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(lookupString).bold(), priority);
  }
}

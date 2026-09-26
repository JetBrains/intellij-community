// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.project.DumbAware;
import com.jetbrains.python.console.PyConsoleUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Suppresses code completion in the input editor of a Python console, in particular while the console passes
 * the typed text to the stdin of the running program.
 *
 * @see PyConsoleUtil#isCodeCompletionSuppressed(com.intellij.psi.PsiFile)
 */
@ApiStatus.Internal
public final class PythonConsoleCompletionBlockingContributor extends CompletionContributor implements DumbAware {
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (PyConsoleUtil.isCodeCompletionSuppressed(parameters.getOriginalFile())) {
      result.stopHere();
    }
  }
}

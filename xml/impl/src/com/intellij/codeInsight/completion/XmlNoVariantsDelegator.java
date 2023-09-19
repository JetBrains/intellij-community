// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
final class XmlNoVariantsDelegator extends CompletionContributor {
  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    boolean empty = result.runRemainingContributors(parameters, true).isEmpty();
    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty && parameters.getCompletionType() == CompletionType.BASIC) {
      XmlCompletionContributor.completeTagName(parameters, result);
    }
  }
}

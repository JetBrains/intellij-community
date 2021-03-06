// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
final class XmlNoVariantsDelegator extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
    boolean empty = result.runRemainingContributors(parameters, true).isEmpty();
    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty && parameters.getCompletionType() == CompletionType.BASIC) {
      XmlCompletionContributor.completeTagName(parameters, result);
    }
  }
}

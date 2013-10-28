package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;

public final class PostfixCompletionContributor extends CompletionContributor {
  public PostfixCompletionContributor() {
    super();

    ((Integer) 1).toString();

    extend(CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      PostfixItemsCompletionProvider.instance);
  }

}
package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixItemsCompletionProvider;

public final class PostfixCompletionContributor extends CompletionContributor {
  public PostfixCompletionContributor() {
    super();

    extend(CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      PostfixItemsCompletionProvider.instance);
  }
}
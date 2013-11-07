package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public final class PostfixCompletionContributor extends CompletionContributor {
  public PostfixCompletionContributor() {
    extend(CompletionType.BASIC,
      PlatformPatterns.psiElement(),
      PostfixItemsCompletionProvider.instance);
  }
}
package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public final class PostfixCompletionContributor extends CompletionContributor {
  @NotNull private final Object myDummyIdentifierLock = new Object();
  @NotNull private String myDummyIdentifier = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;
  public static boolean behaveAsAutoPopupForTests = false;

  @Override public void duringCompletion(@NotNull CompletionInitializationContext context) {
    synchronized (myDummyIdentifierLock) {
      myDummyIdentifier = context.getDummyIdentifier();
    }
  }

  @Override public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    final String dummyIdentifier;
    synchronized (myDummyIdentifierLock) { dummyIdentifier = myDummyIdentifier; }

    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;

    CompletionType completionType = parameters.getCompletionType();
    if (completionType != CompletionType.BASIC) return;

    PostfixExecutionContext executionContext = new PostfixExecutionContext(isForceMode, dummyIdentifier);

    PostfixItemsCompletionProvider.addCompletions(parameters, result, executionContext);
  }
}
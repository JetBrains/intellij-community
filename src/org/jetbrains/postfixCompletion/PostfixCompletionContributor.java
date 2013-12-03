package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.PsiCodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixExecutionContext;
import org.jetbrains.postfixCompletion.infrastructure.PostfixItemsCompletionProvider;
import org.jetbrains.postfixCompletion.infrastructure.PostfixNoVariantsCompletionUtil;

import java.util.LinkedHashSet;

public class PostfixCompletionContributor extends CompletionContributor {
  // I'm really sorry:
  @NotNull private final Object myDummyIdentifierLock = new Object();
  @NotNull private String myDummyIdentifier = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;
  public static boolean behaveAsAutoPopupForTests = false;

  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    synchronized (myDummyIdentifierLock) {
      myDummyIdentifier = context.getDummyIdentifier();
    }
  }

  // todo: control postfix lookup elements to be on top/bottom of the list

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final String dummyIdentifier;
    synchronized (myDummyIdentifierLock) {
      dummyIdentifier = myDummyIdentifier;
    }

    CompletionType completionType = parameters.getCompletionType();
    if (completionType != CompletionType.BASIC) return;

    // todo
    LinkedHashSet<CompletionResult> results = result.runRemainingContributors(parameters, true);

    // build PostfixExecutionContext
    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;
    boolean insideCodeFragment = (parameters.getPosition().getContainingFile() instanceof PsiCodeFragment);

    PostfixExecutionContext executionContext =
      new PostfixExecutionContext(isForceMode, dummyIdentifier, insideCodeFragment);

    // add normal postfix items
    PostfixItemsCompletionProvider.getItems(parameters, result, executionContext);

    PostfixNoVariantsCompletionUtil.suggestChainedCalls(parameters, result, executionContext);
  }
}
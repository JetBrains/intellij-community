package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.infrastructure.*;

import java.util.*;

public final class PostfixCompletionContributor extends CompletionContributor {
  @NotNull private final Object myDummyIdentifierLock = new Object();
  @NotNull private String myDummyIdentifier = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;
  public static boolean behaveAsAutoPopupForTests = false;

  @Override public void duringCompletion(@NotNull CompletionInitializationContext context) {
    synchronized (myDummyIdentifierLock) {
      myDummyIdentifier = context.getDummyIdentifier();
    }
  }

  // todo: control postfix template to be on top/bottom of the list
  // todo: _disable_ postfix items when there is no other one (except over literals?)
  // todo: do not show force items if qualified non-forced items exists (!!!)

  @Override public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final String dummyIdentifier;
    synchronized (myDummyIdentifierLock) { dummyIdentifier = myDummyIdentifier; }

    CompletionType completionType = parameters.getCompletionType();
    if (completionType != CompletionType.BASIC) return;

    LinkedHashSet<CompletionResult> results = result.runRemainingContributors(parameters, true);

    // build PostfixExecutionContext
    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;
    boolean insideCodeFragment = (parameters.getPosition().getContainingFile() instanceof PsiCodeFragment);

    PostfixExecutionContext executionContext =
      new PostfixExecutionContext(isForceMode, dummyIdentifier, insideCodeFragment);

    // add normal postfix items
    PostfixItemsCompletionProvider.getItems(parameters, result, executionContext);


    //if (results.isEmpty()) {
      PostfixNoVariantsCompletionUtil.suggestChainedCalls(parameters, result, executionContext);
    //}
  }





}
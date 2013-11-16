package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

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

  @Override public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final String dummyIdentifier;
    synchronized (myDummyIdentifierLock) { dummyIdentifier = myDummyIdentifier; }

    CompletionType completionType = parameters.getCompletionType();
    if (completionType != CompletionType.BASIC) return;


    LinkedHashSet<CompletionResult> completionResults = result.runRemainingContributors(parameters, true);

    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;

    PostfixExecutionContext executionContext = new PostfixExecutionContext(isForceMode, dummyIdentifier);


    if (!completionResults.isEmpty()) {
      PostfixItemsCompletionProvider.addCompletions(parameters, result, executionContext);
    }


    LinkedHashSet<LookupElement> chainQualifiers = new LinkedHashSet<LookupElement>();
    for (CompletionResult completionResult : completionResults) {
      LookupElement lookupElement = completionResult.getLookupElement();
      if (lookupElement instanceof JavaChainLookupElement) {
        JavaChainLookupElement chainLookupElement = (JavaChainLookupElement) lookupElement;
        LookupElement qualifier = chainLookupElement.getQualifier();
        if (chainQualifiers.add(qualifier)) {

          PsiType exprType = null;
          TypedLookupItem typedLookupItem = qualifier.as(TypedLookupItem.CLASS_CONDITION_KEY);
          if (typedLookupItem != null) exprType = typedLookupItem.getType();


          List<LookupElement> elements = PostfixItemsCompletionProvider.addCompletions2(parameters, executionContext, exprType);
          for (LookupElement postfixElement : elements) {

            JavaChainLookupElement chainedPostfix =
              new JavaChainLookupElement(qualifier, postfixElement) {
                @Override public PsiType getType() { return null; }
              };

            PrefixMatcher prefixMatcher = completionResult.getPrefixMatcher();
            if (!prefixMatcher.prefixMatches(chainedPostfix)) continue;
            CompletionResult postfixResult = completionResult.withLookupElement(chainedPostfix);
            result.passResult(postfixResult);
          }
        }
      }
    }
  }
}
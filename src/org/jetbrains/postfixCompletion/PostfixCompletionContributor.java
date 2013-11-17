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
  // todo: do not show force items if qualified non-forced items exists (!!!)

  @Override public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final String dummyIdentifier;
    synchronized (myDummyIdentifierLock) { dummyIdentifier = myDummyIdentifier; }

    CompletionType completionType = parameters.getCompletionType();
    if (completionType != CompletionType.BASIC) return;


    LinkedHashSet<CompletionResult> results = result.runRemainingContributors(parameters, true);

    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;

    PostfixExecutionContext executionContext = new PostfixExecutionContext(isForceMode, dummyIdentifier);
    PostfixItemsCompletionProvider.addCompletions(parameters, result, executionContext);

    suggestChainedCalls(parameters, result);
  }


  private static void suggestChainedCalls(
      @NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {

    PsiElement position = parameters.getPosition(), parent = position.getParent();
    if ((!(parent instanceof PsiJavaCodeReferenceElement))) return;

    PsiElement qualifier = ((PsiJavaCodeReferenceElement) parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return;

    PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement) qualifier;
    if (qualifierReference.isQualified()) return;

    PsiElement target = qualifierReference.resolve();
    if (target != null && !(target instanceof PsiPackage)) return;

    PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaCodeReferenceCodeFragment) return;

    // "prefix."
    String fullPrefix = parent.getText().substring(
      0, parameters.getOffset() - parent.getTextRange().getStartOffset());

    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);


    for (LookupElement base : PostfixNoVariantsCompletionUtil.suggestQualifierItems(parameters, qualifierReference)) {

      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiType.VOID.equals(type)) {
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base);
        if (ref != null) {
          // todo:

          List<LookupElement> elements = PostfixItemsCompletionProvider.addCompletions2(
            parameters, new PostfixExecutionContext(false, "postfix"), type);

          for (LookupElement postfixElement : elements) {

            JavaChainLookupElement chainedPostfix =
              new JavaChainLookupElement(base, postfixElement) {
                @Override public PsiType getType() {
                  return null;
                }
              };

            qualifiedCollector.addElement(chainedPostfix);

            //PrefixMatcher prefixMatcher = completionResult.getPrefixMatcher();
            //if (!prefixMatcher.prefixMatches(chainedPostfix)) continue;
            //CompletionResult postfixResult = completionResult.withLookupElement(chainedPostfix);
            //result.passResult(postfixResult);
          }

          // TODO: create chain here
        }
      }
    }
  }


}
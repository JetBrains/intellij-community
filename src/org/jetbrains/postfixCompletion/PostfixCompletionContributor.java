package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.*;
import com.intellij.codeInsight.completion.scope.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.impl.source.resolve.reference.impl.*;
import com.intellij.psi.infos.*;
import com.intellij.psi.search.*;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

import java.util.*;
import java.util.LinkedHashSet;

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


    LinkedHashSet<CompletionResult> completionResults = result
      .withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE)
      .runRemainingContributors(parameters, true);

    boolean isForceMode = !parameters.isAutoPopup() && !behaveAsAutoPopupForTests;

    PostfixExecutionContext executionContext = new PostfixExecutionContext(isForceMode, dummyIdentifier);


    //if (!completionResults.isEmpty()) {
      PostfixItemsCompletionProvider.addCompletions(parameters, result, executionContext);
    //}


    /*LinkedHashSet<LookupElement> chainQualifiers = new LinkedHashSet<LookupElement>();
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
    }*/

    suggestChainedCalls(parameters, result, parameters.getPosition());
  }


  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result, PsiElement position) {
    PsiElement parent = position.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) {
      return;
    }
    PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement) ||
      ((PsiJavaCodeReferenceElement)qualifier).isQualified()) {
      return;
    }
    PsiElement target = ((PsiJavaCodeReferenceElement)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
      return;
    }

    PsiFile file = position.getContainingFile();
    if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      return;
    }

    String fullPrefix = parent.getText().substring(0, parameters.getOffset() - parent.getTextRange().getStartOffset());
    CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    for (LookupElement base : suggestQualifierItems(parameters, (PsiJavaCodeReferenceElement)qualifier, filter)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiType.VOID.equals(type)) {
        PsiReferenceExpression ref = ReferenceExpressionCompletionContributor.createMockReference(position, type, base);
        if (ref != null) {

          List<LookupElement> elements = PostfixItemsCompletionProvider.addCompletions2(
            parameters, new PostfixExecutionContext(false, "postfix"), type);

          for (LookupElement postfixElement : elements) {

            JavaChainLookupElement chainedPostfix =
              new JavaChainLookupElement(base, postfixElement) {
                @Override public PsiType getType() { return null; }
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

  private static Set<LookupElement> suggestQualifierItems(CompletionParameters parameters,
                                                          PsiJavaCodeReferenceElement qualifier,
                                                          ElementFilter filter) {


    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }

    PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    Set<LookupElement> plainVariants = completeReference(qualifier, qualifier, filter, true, true, parameters, qMatcher);

    for (PsiClass aClass : PsiShortNamesCache.getInstance(qualifier.getProject()).getClassesByName(referenceName, qualifier.getResolveScope())) {
      plainVariants.add(JavaClassNameCompletionContributor.createClassLookupItem(aClass, true));
    }

    if (!plainVariants.isEmpty()) {
      return plainVariants;
    }

    final Set<LookupElement> allClasses = new LinkedHashSet<LookupElement>();
    JavaClassNameCompletionContributor.addAllClasses(parameters.withPosition(qualifier.getReferenceNameElement(), qualifier.getTextRange().getEndOffset()),
      true, qMatcher, new CollectConsumer<LookupElement>(allClasses));
    return allClasses;
  }

  static Set<LookupElement> completeReference(final PsiElement element,
                                              PsiReference reference,
                                              final ElementFilter filter,
                                              final boolean acceptClasses,
                                              final boolean acceptMembers,
                                              CompletionParameters parameters, final PrefixMatcher matcher) {
    if (reference instanceof PsiMultiReference) {
      reference = ContainerUtil.findInstance(((PsiMultiReference) reference).getReferences(), PsiJavaReference.class);
    }

    if (reference instanceof PsiJavaReference) {
      final PsiJavaReference javaReference = (PsiJavaReference)reference;

      ElementFilter checkClass = new ElementFilter() {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return filter.isAcceptable(element, context);
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
          if (ReflectionCache.isAssignable(PsiClass.class, hintClass)) {
            return acceptClasses;
          }

          if (ReflectionCache.isAssignable(PsiVariable.class, hintClass) ||
            ReflectionCache.isAssignable(PsiMethod.class, hintClass) ||
            ReflectionCache.isAssignable(CandidateInfo.class, hintClass)) {
            return acceptMembers;
          }
          return false;
        }
      };
      JavaCompletionProcessor.Options options =
        JavaCompletionProcessor.Options.DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1);
      return JavaCompletionUtil.processJavaReference(element, javaReference, checkClass, options, matcher, parameters);
    }

    return Collections.emptySet();
  }
}
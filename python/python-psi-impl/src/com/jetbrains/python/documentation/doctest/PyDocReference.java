// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public class PyDocReference extends PyReferenceImpl {
  public PyDocReference(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    final HighlightSeverity severity = super.getUnresolvedHighlightSeverity(context);
    return severity != null ? HighlightSeverity.WARNING : null;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    ResolveResult[] results = super.multiResolve(incompleteCode);
    if (results.length == 0) {
      final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
      final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
      final String referencedName = myElement.getReferencedName();
      if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

      if (host != null) {
        final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
        if (files != null) {
          for (Pair<PsiElement, TextRange> pair : files) {
            if (pair.getFirst() instanceof PyFile) {
              final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

              PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)pair.getFirst(), referencedName, pair.getFirst());
              final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, pair.getFirst(),
                                                                                  pair.getFirst());
              if (!resultList.isEmpty()) {
                List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
              }
            }
          }
        }
        final PyResolveProcessor processor = new PyResolveProcessor(referencedName);
        final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(host);
        if (scopeOwner != null) {
          final PsiFile topLevel = scopeOwner.getContainingFile();
          PyResolveUtil.scopeCrawlUp(processor, scopeOwner, referencedName, topLevel);
          final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, null, topLevel);
          if (!resultList.isEmpty()) {
            final List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
            return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
          }
        }
      }
    }
    return results;
  }

  @Override
  public @NotNull Object @NotNull [] getVariants() {
    final Object[] results = super.getVariants();

    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
    if (host == null) return results;

    final PyQualifiedExpression originalElement = CompletionUtilCoreImpl.getOriginalElement(myElement);
    final PyQualifiedExpression element = originalElement != null ? originalElement : myElement;
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element, null, filterForPresentedNames(results));

    final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
    if (files != null) {
      for (Pair<PsiElement, TextRange> pair : files) {
        if (pair.getFirst() instanceof PyFile) {
          PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)pair.getFirst(), null, pair.getFirst());
        }
      }
    }
    final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(host);
    if (scopeOwner != null) {
      PyResolveUtil.scopeCrawlUp(processor, scopeOwner, null, scopeOwner.getContainingFile());
    }

    return ArrayUtil.mergeArrayAndCollection(results, processor.getResultList(), Object[]::new);
  }

  private static @Nullable Condition<String> filterForPresentedNames(@NotNull Object[] variants) {
    if (variants.length == 0) return null;
    final Set<String> seenNames = StreamEx.of(variants).select(LookupElement.class).map(LookupElement::getLookupString).toSet();
    return s -> !seenNames.contains(s);
  }
}

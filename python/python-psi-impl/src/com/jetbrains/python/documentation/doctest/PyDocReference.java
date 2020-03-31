// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyUtil.StringNodeInfo;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        final List<Pair<PsiElement,TextRange>> files = languageManager.getInjectedPsiFiles(host);
        if (files != null) {
          for (Pair<PsiElement, TextRange> pair : files) {
            if (pair.getFirst() instanceof PyFile) {
              final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

              PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)pair.getFirst(), referencedName, pair.getFirst());
              final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, pair.getFirst(),
                                                                                  pair.getFirst());
              if (resultList.size() > 0) {
                List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
              }
            }
          }
        }
        final PyResolveProcessor processor = new PyResolveProcessor(referencedName);
        final ScopeOwner scopeOwner = getHostScopeOwner();
        if (scopeOwner != null) {
          final PsiFile topLevel = scopeOwner.getContainingFile();
          PyResolveUtil.scopeCrawlUp(processor, scopeOwner, referencedName, topLevel);
          final PsiElement referenceAnchor = getScopeControlFlowAnchor(host);
          final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, referenceAnchor, topLevel);
          if (resultList.size() > 0) {
            final List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
            return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
          }
        }
      }
    }
    return results;
  }

  @Nullable
  private PsiElement getScopeControlFlowAnchor(@NotNull PsiLanguageInjectionHost host) {
    if (isInsideFormattedStringNode(host)) {
      return getControlFlowAnchorForFString((PyStringLiteralExpression)host);
    }
    return null;
  }

  @Nullable
  public static PsiElement getControlFlowAnchorForFString(@NotNull PyStringLiteralExpression host) {
    final PsiElement comprehensionPart = PsiTreeUtil.findFirstParent(host, PyDocReference::isComprehensionResultOrComponent);
    if (comprehensionPart != null) {
      return comprehensionPart;
    }
    return PsiTreeUtil.getParentOfType(host, PyStatement.class);
  }

  private static boolean isComprehensionResultOrComponent(@NotNull PsiElement element) {
    // Any comprehension component and its result are represented as children expressions of the comprehension element.
    // Only they have respective nodes in CFG and thus can be used as anchors for getResultsFromProcessor().
    return element instanceof PyExpression && element.getParent() instanceof PyComprehensionElement;
  }

  private boolean isInsideFormattedStringNode(@NotNull PsiLanguageInjectionHost host) {
    if (host instanceof PyStringLiteralExpression) {
      final ASTNode node = findContainingStringNode(getElement(), (PyStringLiteralExpression)host);
      return node != null && new StringNodeInfo(node).isFormatted();
    }
    return false;
  }

  @Nullable
  private static ASTNode findContainingStringNode(@NotNull PsiElement injectedElement, @NotNull PyStringLiteralExpression host) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(host.getProject());
    final List<Pair<PsiElement, TextRange>> files = manager.getInjectedPsiFiles(host);
    if (files != null) {
      final PsiFile injectedFile = injectedElement.getContainingFile();
      final Pair<PsiElement, TextRange> first = ContainerUtil.find(files, pair -> pair.getFirst() == injectedFile);
      if (first != null) {
        final int hostOffset = -host.getTextRange().getStartOffset();
        for (ASTNode node : host.getStringNodes()) {
          final TextRange relativeNodeRange = node.getTextRange().shiftRight(hostOffset);
          if (relativeNodeRange.contains(first.getSecond())) {
            return node;
          }
        }
      }
    }
    return null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final ArrayList<Object> ret = Lists.newArrayList(super.getVariants());
    final PyQualifiedExpression originalElement = CompletionUtilCoreImpl.getOriginalElement(myElement);
    final PyQualifiedExpression element = originalElement != null ? originalElement : myElement;

    final ScopeOwner scopeOwner = getHostScopeOwner();
    if (scopeOwner != null) {
      final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element);
      PyResolveUtil.scopeCrawlUp(processor, scopeOwner, null, null);
      ret.addAll(processor.getResultList());
    }
    return ret.toArray();
  }


  @Nullable
  private ScopeOwner getHostScopeOwner() {
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
    if (host != null) {
      final PsiFile file = host.getContainingFile();
      ScopeOwner result = ScopeUtil.getScopeOwner(host);
      if (result == null && file instanceof ScopeOwner) {
        result = (ScopeOwner)file;
      }
      return result;
    }
    return null;
  }
}

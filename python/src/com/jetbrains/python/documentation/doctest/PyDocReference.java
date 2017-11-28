/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.doctest;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtil;
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

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
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
            final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

            PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)pair.getFirst(), referencedName, pair.getFirst());
            final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, pair.getFirst(),
                                                                                pair.getFirst());
            if (resultList.size() > 0) {
              List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
              return ret.toArray(new RatedResolveResult[ret.size()]);
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
            return ret.toArray(new RatedResolveResult[ret.size()]);
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

  @NotNull
  public Object[] getVariants() {
    final ArrayList<Object> ret = Lists.newArrayList(super.getVariants());
    final PsiElement originalElement = CompletionUtil.getOriginalElement(myElement);
    final PyQualifiedExpression element = originalElement instanceof PyQualifiedExpression ?
                                          (PyQualifiedExpression)originalElement : myElement;

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

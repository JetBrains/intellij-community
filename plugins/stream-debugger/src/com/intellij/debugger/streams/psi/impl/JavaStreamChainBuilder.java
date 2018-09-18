// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainDetector;
import com.intellij.debugger.streams.psi.ChainTransformer;
import com.intellij.debugger.streams.psi.PsiUtil;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaStreamChainBuilder implements StreamChainBuilder {
  private final MyStreamChainExistenceChecker myExistenceChecker = new MyStreamChainExistenceChecker();

  private final ChainTransformer.Java myChainTransformer;
  private final ChainDetector myDetector;

  public JavaStreamChainBuilder(@NotNull ChainTransformer.Java transformer, @NotNull ChainDetector detector) {
    myChainTransformer = transformer;
    myDetector = detector;
  }

  @Override
  public boolean isChainExists(@NotNull PsiElement startElement) {
    PsiElement current = getLatestElementInCurrentScope(PsiUtil.ignoreWhiteSpaces(startElement));
    while (current != null) {
      myExistenceChecker.reset();
      current.accept(myExistenceChecker);
      if (myExistenceChecker.found()) {
        return true;
      }
      current = toUpperLevel(current);
    }

    return false;
  }

  @NotNull
  @Override
  public List<StreamChain> build(@NotNull PsiElement startElement) {
    final MyChainCollectorVisitor visitor = new MyChainCollectorVisitor();

    PsiElement current = getLatestElementInCurrentScope(PsiUtil.ignoreWhiteSpaces(startElement));
    while (current != null) {
      current.accept(visitor);
      current = toUpperLevel(current);
    }

    final List<List<PsiMethodCallExpression>> chains = visitor.getPsiChains();
    return buildChains(chains, startElement);
  }

  @Nullable
  private static PsiElement toUpperLevel(@NotNull PsiElement element) {
    element = element.getParent();
    while (element != null && !(element instanceof PsiLambdaExpression) && !(element instanceof PsiAnonymousClass)) {
      element = element.getParent();
    }

    return getLatestElementInCurrentScope(element);
  }

  @Nullable
  @Contract("null -> null")
  private static PsiElement getLatestElementInCurrentScope(@Nullable PsiElement element) {
    PsiElement current = element;
    while (current != null) {
      final PsiElement parent = current.getParent();

      if (parent instanceof PsiModifiableCodeBlock || parent instanceof PsiLambdaExpression || parent instanceof PsiStatement) {
        break;
      }

      current = parent;
    }

    return current;
  }

  @NotNull
  private List<StreamChain> buildChains(@NotNull List<List<PsiMethodCallExpression>> chains, @NotNull PsiElement context) {
    return chains.stream().map(x -> myChainTransformer.transform(x, context)).collect(Collectors.toList());
  }

  private class MyStreamChainExistenceChecker extends MyVisitorBase {
    private boolean myFound = false;

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (myFound) return;
      super.visitMethodCallExpression(expression);
      if (!myFound && myDetector.isTerminationCall(expression)) {
        myFound = true;
      }
    }

    void reset() {
      myFound = false;
    }

    boolean found() {
      return myFound;
    }
  }

  private class MyChainCollectorVisitor extends MyVisitorBase {
    private final Set<PsiMethodCallExpression> myTerminationCalls = new HashSet<>();
    private final Map<PsiMethodCallExpression, PsiMethodCallExpression> myPreviousCalls = new HashMap<>();

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!myPreviousCalls.containsKey(expression) && myDetector.isStreamCall(expression)) {
        updateCallTree(expression);
      }
    }

    private void updateCallTree(@NotNull PsiMethodCallExpression expression) {
      if (myDetector.isTerminationCall(expression)) {
        myTerminationCalls.add(expression);
      }

      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiReferenceExpression)) return;
      final PsiElement parentCall = parent.getParent();
      if (parentCall instanceof PsiMethodCallExpression && myDetector.isStreamCall((PsiMethodCallExpression)parentCall)) {
        final PsiMethodCallExpression parentCallExpression = (PsiMethodCallExpression)parentCall;
        myPreviousCalls.put(parentCallExpression, expression);
        updateCallTree(parentCallExpression);
      }
    }

    @NotNull
    List<List<PsiMethodCallExpression>> getPsiChains() {
      final List<List<PsiMethodCallExpression>> chains = new ArrayList<>();
      for (final PsiMethodCallExpression terminationCall : myTerminationCalls) {
        List<PsiMethodCallExpression> chain = new ArrayList<>();
        PsiMethodCallExpression current = terminationCall;
        while (current != null) {
          if (!myDetector.isIntermediateCall(current) && !myDetector.isTerminationCall(current)) break;
          chain.add(current);
          current = myPreviousCalls.get(current);
        }

        Collections.reverse(chain);
        chains.add(chain);
      }

      return chains;
    }
  }

  private static class MyVisitorBase extends JavaRecursiveElementVisitor {
    @Override
    public void visitCodeBlock(PsiCodeBlock block) {
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
    }
  }
}

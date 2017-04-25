/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.StreamApiUtil;
import com.intellij.debugger.streams.psi.StreamChainTransformer;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.debugger.streams.wrapper.StreamChainBuilder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class AdvancedStreamChainBuilder implements StreamChainBuilder {
  private static final MyStreamChainExistenceChecker myExistenceChecker = new MyStreamChainExistenceChecker();

  private final StreamChainTransformer myChainTransformer;

  public AdvancedStreamChainBuilder(@NotNull StreamChainTransformer transformer) {
    myChainTransformer = transformer;
  }

  @Override
  public boolean isChainExists(@NotNull PsiElement startElement) {
    PsiElement current = startElement;
    while (current != null && current.getChildren().length == 0) {
      current = current.getNextSibling();
    }

    if (current == null) return false;

    myExistenceChecker.reset();
    current.accept(myExistenceChecker);
    return myExistenceChecker.found();
  }

  @NotNull
  @Override
  public List<StreamChain> build(@NotNull PsiElement startElement) {
    final MyChainCollectorVisitor visitor = new MyChainCollectorVisitor();
    PsiElement current = startElement;
    while (current != null && current.getChildren().length == 0) {
      current = current.getNextSibling();
    }

    if (current == null) return Collections.emptyList();

    current.accept(visitor);
    final List<List<PsiMethodCallExpression>> chains = visitor.getPsiChains();
    return buildChains(chains, current);
  }

  @NotNull
  private List<StreamChain> buildChains(@NotNull List<List<PsiMethodCallExpression>> chains, @NotNull PsiElement context) {
    return chains.stream().map(x -> myChainTransformer.transform(x, context)).collect(Collectors.toList());
  }

  private static class MyStreamChainExistenceChecker extends MyVisitorBase {
    private boolean myFound = false;

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (myFound) return;
      super.visitMethodCallExpression(expression);
      if (!myFound && StreamApiUtil.isTerminationStreamCall(expression)) {
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

  private static class MyChainCollectorVisitor extends MyVisitorBase {
    private final Set<PsiMethodCallExpression> myTerminationCalls = new HashSet<>();
    private final Map<PsiMethodCallExpression, PsiMethodCallExpression> myPreviousCalls = new HashMap<>();

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!myPreviousCalls.containsKey(expression) && StreamApiUtil.isStreamCall(expression)) {
        updateCallTree(expression);
      }
    }

    private void updateCallTree(@NotNull PsiMethodCallExpression expression) {
      if (StreamApiUtil.isTerminationStreamCall(expression)) {
        myTerminationCalls.add(expression);
      }

      final PsiElement parent = expression.getParent();
      if (parent == null) return;
      final PsiElement parentCall = parent.getParent();
      if (parentCall instanceof PsiMethodCallExpression && StreamApiUtil.isStreamCall((PsiMethodCallExpression)parentCall)) {
        final PsiMethodCallExpression parentCallExpression = (PsiMethodCallExpression)parentCall;
        myPreviousCalls.put(parentCallExpression, expression);
        updateCallTree(parentCallExpression);
      }
    }

    List<List<PsiMethodCallExpression>> getPsiChains() {
      final List<List<PsiMethodCallExpression>> chains = new ArrayList<>();
      for (final PsiMethodCallExpression terminationCall : myTerminationCalls) {
        final List<PsiMethodCallExpression> chain = new ArrayList<>();
        PsiMethodCallExpression current = terminationCall;
        while (current != null) {
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

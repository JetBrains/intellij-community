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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiElement current = getLatestElementInCurrentScope(ignoreWhiteSpaces(startElement));
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

    PsiElement current = getLatestElementInCurrentScope(ignoreWhiteSpaces(startElement));
    while (current != null) {
      current.accept(visitor);
      current = toUpperLevel(current);
    }

    final List<List<PsiMethodCallExpression>> chains = visitor.getPsiChains();
    return buildChains(chains, startElement);
  }

  @NotNull
  private static PsiElement ignoreWhiteSpaces(@NotNull PsiElement element) {
    PsiElement result = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
    if (result == null) {
      result = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      if (result == null) {
        result = element;
      }
    }

    return result;
  }

  @Nullable
  private static PsiElement toUpperLevel(@NotNull PsiElement element) {
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

      if (parent instanceof PsiCodeBlock || parent instanceof PsiLambdaExpression || parent instanceof PsiStatement) {
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

    @NotNull
    List<List<PsiMethodCallExpression>> getPsiChains() {
      final List<List<PsiMethodCallExpression>> chains = new ArrayList<>();
      for (final PsiMethodCallExpression terminationCall : myTerminationCalls) {
        final List<PsiMethodCallExpression> chain = new ArrayList<>();
        PsiMethodCallExpression current = terminationCall;
        while (current != null) {
          chain.add(current);
          if (StreamApiUtil.isProducerStreamCall(current)) {
            break;
          }
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

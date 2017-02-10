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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class TypeEvalStack {
  private static ThreadLocal<TypeEvalStack> STACK = new ThreadLocal<TypeEvalStack>() {
    @Override
    protected TypeEvalStack initialValue() {
      return new TypeEvalStack();
    }
  };

  private final Set<PsiElement> myBeingEvaluated = new HashSet<>();

  public static boolean mayEvaluate(PsiElement element) {
    final TypeEvalStack curStack = STACK.get();
    if (curStack.myBeingEvaluated.contains(element)) {
      return false;
    }
    curStack.myBeingEvaluated.add(element);
    return true;
  }

  public static void evaluated(PsiElement element) {
    final TypeEvalStack curStack = STACK.get();
    curStack.myBeingEvaluated.remove(element);
  }
}

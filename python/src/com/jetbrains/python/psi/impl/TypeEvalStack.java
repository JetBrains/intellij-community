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

  private final Set<PsiElement> myBeingEvaluated = new HashSet<PsiElement>();

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

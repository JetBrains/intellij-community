package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiMethod;

/**
 * Java doc matching strategy
 */
public final class JavaDocMatchingStrategy extends MatchingStrategyBase {
  @Override public void visitClass(final PsiClass clazz) {
    result = true;
  }

  @Override public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  @Override public void visitMethod(final PsiMethod method) {
    result = true;
  }

  private JavaDocMatchingStrategy() {}

  private static class JavaDocMatchingStrategyHolder {
    private static final JavaDocMatchingStrategy instance = new JavaDocMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return JavaDocMatchingStrategyHolder.instance;
  }
}

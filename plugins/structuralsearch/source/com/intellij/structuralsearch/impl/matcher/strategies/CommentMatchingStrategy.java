package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;

/**
 * Java doc matching strategy
 */
public final class CommentMatchingStrategy extends MatchingStrategyBase {
  public void visitClass(final PsiClass clazz) {
    result = true;
  }

  public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  public void visitMethod(final PsiMethod method) {
    result = true;
  }

  public void visitComment(final PsiComment comment) {
    result = true;
  }

  private CommentMatchingStrategy() {}
  private static CommentMatchingStrategy instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new CommentMatchingStrategy();
    return instance;
  }
}

package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiMethod;

/**
 * Java doc matching strategy
 */
public final class CommentMatchingStrategy extends MatchingStrategyBase {
  @Override public void visitClass(final PsiClass clazz) {
    result = true;
  }

  @Override public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  @Override public void visitMethod(final PsiMethod method) {
    result = true;
  }

  @Override public void visitComment(final PsiComment comment) {
    result = true;
  }

  private CommentMatchingStrategy() {}

  private static class CommentMatchingStrategyHolder {
    private static final CommentMatchingStrategy instance = new CommentMatchingStrategy();
  }

  public static MatchingStrategy getInstance() {
    return CommentMatchingStrategyHolder.instance;
  }
}

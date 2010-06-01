package com.intellij.structuralsearch;

import com.intellij.lang.javascript.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

/**
 * @author Eugene.Kudelevsky
 */
public class JSMatchingStrategy extends JSElementVisitor implements MatchingStrategy {
  private static final JSMatchingStrategy ourInstance = new JSMatchingStrategy();

  private boolean result = false;

  public static JSMatchingStrategy getInstance() {
    return ourInstance;
  }

  @Override
  public void visitJSSourceElement(JSElement node) {
    result = true;
  }

  @Override
  public void visitJSExpression(JSExpression node) {
    result = true;
  }

  @Override
  public void visitJSVariable(JSVariable node) {
    result = true;
  }

  @Override
  public void visitJSProperty(JSProperty node) {
    result = true;
  }

  @Override
  public void visitJSCaseClause(JSCaseClause node) {
    result = true;
  }

  @Override
  public void visitJSCatchBlock(JSCatchBlock node) {
    result = true;
  }

  @Override
  public void visitJSArgumentList(JSArgumentList node) {
    result = true;
  }

  @Override
  public void visitJSParameterList(JSParameterList node) {
    result = true;
  }

  public boolean continueMatching(PsiElement start) {
    result = false;
    start.accept(this);
    return result;
  }
}

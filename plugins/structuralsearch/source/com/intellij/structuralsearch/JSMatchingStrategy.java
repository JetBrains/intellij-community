package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class JSMatchingStrategy extends JSElementVisitor implements MatchingStrategy {
  private static final JSMatchingStrategy ourInstance = new JSMatchingStrategy(JavascriptLanguage.INSTANCE);
  private static final JSMatchingStrategy ourInstanceEcma = new JSMatchingStrategy(JavaScriptSupportLoader.ECMA_SCRIPT_L4);

  private boolean result = false;

  private final Language myLanguage;

  private JSMatchingStrategy(@NotNull Language language) {
    myLanguage = language;
  }

  public static JSMatchingStrategy getInstance() {
    return ourInstance;
  }

  public static JSMatchingStrategy getInstanceEcma() {
    return ourInstanceEcma;
  }

  @Override
  public void visitElement(PsiElement element) {
    super.visitElement(element);
    if (element instanceof JSFile) {
      result = true;
    }
    if (JSStructuralSearchProfile.getLanguageForElement(element) != myLanguage) {
      result = false;
    }
  }

  @Override
  public void visitJSSourceElement(JSElement node) {
    result = true;
    super.visitJSSourceElement(node);
  }

  @Override
  public void visitJSExpression(JSExpression node) {
    result = true;
    super.visitJSExpression(node);
  }

  @Override
  public void visitJSVariable(JSVariable node) {
    result = true;
    super.visitJSVariable(node);
  }

  @Override
  public void visitJSProperty(JSProperty node) {
    result = true;
    super.visitJSProperty(node);
  }

  @Override
  public void visitJSCaseClause(JSCaseClause node) {
    result = true;
    super.visitJSCaseClause(node);
  }

  @Override
  public void visitJSCatchBlock(JSCatchBlock node) {
    result = true;
    super.visitJSCatchBlock(node);
  }

  @Override
  public void visitJSArgumentList(JSArgumentList node) {
    result = true;
    super.visitJSArgumentList(node);
  }

  @Override
  public void visitJSParameterList(JSParameterList node) {
    result = true;
    super.visitJSParameterList(node);
  }

  @Override
  public void visitJSFunctionDeclaration(JSFunction node) {
    result = true;
    super.visitJSFunctionDeclaration(node);
  }

  @Override
  public void visitJSFunctionExpression(JSFunctionExpression node) {
    result = true;
    super.visitJSFunctionExpression(node);
  }

  @Override
  public void visitJSClass(JSClass aClass) {
    result = true;
    super.visitJSClass(aClass);
  }

  @Override
  public void visitJSAttributeList(JSAttributeList attributeList) {
    result = true;
    super.visitJSAttributeList(attributeList);
  }

  public boolean continueMatching(PsiElement start) {
    result = false;
    start.accept(this);
    return result;
  }
}

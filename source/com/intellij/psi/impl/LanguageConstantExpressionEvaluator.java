package com.intellij.psi.impl;

import com.intellij.lang.LanguageExtension;

/**
 * @author Serega.Vasiliev
 */
public class LanguageConstantExpressionEvaluator extends LanguageExtension<ConstantExpressionEvaluator> {
  public static final LanguageConstantExpressionEvaluator INSTANCE = new LanguageConstantExpressionEvaluator();

  private LanguageConstantExpressionEvaluator() {
    super("com.intellij.constantExpressionEvaluator");
  }
}


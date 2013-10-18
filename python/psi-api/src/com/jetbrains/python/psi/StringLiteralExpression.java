package com.jetbrains.python.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * A generic string literal interface (used both in Python code and template files).
 *
 * @author yole
 */
public interface StringLiteralExpression extends PsiElement {
  String getStringValue();
  TextRange getStringValueTextRange();
}

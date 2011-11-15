package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLanguageInjectionHost;

import java.util.List;

public interface PyStringLiteralExpression extends PyLiteralExpression, PsiLanguageInjectionHost {
  String getStringValue();

  List<TextRange> getStringValueTextRanges();

  List<ASTNode> getStringNodes();

  int valueOffsetToTextOffset(int valueOffset);
}

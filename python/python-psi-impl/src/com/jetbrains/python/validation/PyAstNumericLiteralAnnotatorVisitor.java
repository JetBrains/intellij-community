package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstNumericLiteralExpression;
import org.jetbrains.annotations.NotNull;

public class PyAstNumericLiteralAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyAstNumericLiteralAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyNumericLiteralExpression(@NotNull PyAstNumericLiteralExpression node) {
    String suffix = node.getIntegerLiteralSuffix();
    if (suffix == null || "l".equalsIgnoreCase(suffix)) return;
    if (node.getContainingFile().getLanguage() != PythonLanguage.getInstance()) return;
    myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("INSP.python.trailing.suffix.not.support", suffix))
      .range(node).create();
  }
}

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPatternArgumentList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PyPatternArgumentListImpl extends PyElementImpl implements PyPatternArgumentList {
  public PyPatternArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPatternArgumentList(this);
  }

  @Override
  public @NotNull List<PyPattern> getPatterns() {
    return Arrays.asList(findChildrenByClass(PyPattern.class));
  }
}

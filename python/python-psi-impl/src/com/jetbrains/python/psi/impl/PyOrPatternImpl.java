package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyOrPattern;
import com.jetbrains.python.psi.PyPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PyOrPatternImpl extends PyElementImpl implements PyOrPattern {
  public PyOrPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyOrPattern(this);
  }

  @Override
  public @NotNull List<PyPattern> getAlternatives() {
    return Arrays.asList(findChildrenByClass(PyPattern.class));
  }

  @Override
  public boolean isIrrefutable() {
    return ContainerUtil.exists(getAlternatives(), PyPattern::isIrrefutable);
  }
}

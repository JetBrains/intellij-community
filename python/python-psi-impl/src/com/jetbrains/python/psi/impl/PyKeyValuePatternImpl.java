package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PyKeyValuePatternImpl extends PyElementImpl implements PyKeyValuePattern {
  public PyKeyValuePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeyValuePattern(this);
  }

  @Override
  public @NotNull PyPattern getKeyPattern() {
    return findNotNullChildByClass(PyPattern.class);
  }

  @Override
  public @Nullable PyPattern getValuePattern() {
    return ObjectUtils.tryCast(getLastChild(), PyPattern.class);
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}

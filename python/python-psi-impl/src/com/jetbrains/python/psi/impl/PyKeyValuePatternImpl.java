package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class PyKeyValuePatternImpl extends PyElementImpl implements PyKeyValuePattern {
  public PyKeyValuePatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeyValuePattern(this);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType keyType = context.getType(getKeyPattern());
    final PyPattern value = getValuePattern();
    PyType valueType = null;
    if (value != null) {
      valueType = context.getType(value);
    }
    return PyTupleType.create(this, Arrays.asList(keyType, valueType));
  }
}

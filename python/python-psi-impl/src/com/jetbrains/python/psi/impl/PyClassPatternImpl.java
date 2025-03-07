package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyClassPatternImpl extends PyElementImpl implements PyClassPattern {
  public PyClassPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyClassPattern(this);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, TypeEvalContext.@NotNull Key key) {
    final PyType type = context.getType(getClassNameReference());
    if (type instanceof PyClassType classType) {
      final PyType instanceType = classType.toInstance();
      final PyType captureType = PyCapturePatternImpl.getCaptureType(this, context);
      if (PyTypeChecker.match(captureType, instanceType, context)) {
        return instanceType;
      }
    }
    return null;
  }
}

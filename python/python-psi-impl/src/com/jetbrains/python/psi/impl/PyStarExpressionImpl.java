// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyStarExpressionImpl extends PyElementImpl implements PyStarExpression, PyInstantTypeProvider {
  public PyStarExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    PyExpression operand = getExpression();
    if (operand == null || !isUnpacking() || getParent() instanceof PySubscriptionExpression) {
      // A star expression used as an assignment target (`*a, b = ...`) has no meaningful type of its own,
      // and outside an unpacking context an unpacked tuple type would be misleading.
      return PyAnyType.getUnknown();
    }
    PyType operandType = context.getType(operand);
    if (operandType instanceof PyTupleType tupleType) {
      return tupleType.asUnpackedTupleType();
    }
    // Any other iterable contributes an unbounded `*tuple[T, ...]` portion of its item type.
    if (operandType instanceof PyClassType operandClassType && PyABCUtil.isSubtype(operandType, PyNames.ITERABLE, context)) {
      PyType itemType = PyTypeChecker.getIteratedItemType(operandClassType, context);
      return PyUnpackedTupleTypeImpl.createUnbound(itemType);
    }
    return PyAnyType.getUnknown();
  }

  @Override
  public void acceptPyVisitor(PyElementVisitor visitor) {
    visitor.visitPyStarExpression(this);
  }
}

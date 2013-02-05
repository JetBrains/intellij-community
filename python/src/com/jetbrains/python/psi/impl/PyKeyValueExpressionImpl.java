package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyKeyValueExpressionImpl extends PyElementImpl implements PyKeyValueExpression {
  public PyKeyValueExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType keyType = context.getType(getKey());
    final PyExpression value = getValue();
    PyType valueType = null;
    if (value != null) {
      valueType = context.getType(value);
    }
    return PyTupleType.create(this, new PyType[] {keyType, valueType});
  }

  @NotNull
  public PyExpression getKey() {
    return (PyExpression)getNode().getFirstChildNode().getPsi();
  }

  @Nullable
  public PyExpression getValue() {
    return PsiTreeUtil.getNextSiblingOfType(getKey(), PyExpression.class);
  }
}

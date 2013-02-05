package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySliceExpressionImpl extends PyElementImpl implements PySliceExpression {
  public PySliceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType type = context.getType(getOperand());
    // TODO: Currently we don't evaluate the static range of the slice, so we have to return a generic tuple type without elements
    if (type instanceof PyTupleType) {
      return PyBuiltinCache.getInstance(this).getTupleType();
    }
    return type;
  }

  @NotNull
  @Override
  public PyExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Nullable
  @Override
  public PySliceItem getSliceItem() {
    return PsiTreeUtil.getChildOfType(this, PySliceItem.class);
  }
}

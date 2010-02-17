package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceExpression;
import com.jetbrains.python.psi.PySliceItem;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySliceExpressionImpl extends PyElementImpl implements PySliceExpression {
  public PySliceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return getOperand().getType();
  }

  public PyExpression getOperand() {
    return childToPsiNotNull(PyElementTypes.EXPRESSIONS, 0);
  }

  @Nullable
  public PySliceItem getSliceItem() {
    return PsiTreeUtil.getChildOfType(this, PySliceItem.class);
  }
}

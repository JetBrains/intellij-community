package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator;

/**
 * @author yole
 */
public class PyTupleType extends PyClassType implements PySubscriptableType {
  private final PyType[] myElementTypes;

  public PyTupleType(PsiElement tuple, PyType[] elementTypes) {
    super(PyBuiltinCache.getInstance(tuple).getClass("tuple"), false);
    myElementTypes = elementTypes;
  }

  public String getName() {
    return "tuple(" + StringUtil.join(myElementTypes, new NullableFunction<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType.getName();
      }
    }, ",") + ")";
  }

  public PyType getElementType(PyExpression index) {
    final Object value = PyConstantExpressionEvaluator.evaluate(index);
    if (value instanceof Integer) {
      int iIndex = ((Integer)value).intValue();
      if (iIndex >= 0 && iIndex < myElementTypes.length) {
        return myElementTypes[iIndex];
      }
    }
    return PyNoneType.INSTANCE;
  }
}

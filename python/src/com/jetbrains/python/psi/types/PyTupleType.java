package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator;

import java.util.Arrays;

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
    return "tuple(" + StringUtil.join(myElementTypes, new Function<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType == null ? "unknown" : pyType.getName();
      }
    }, ",") + ")";
  }

  public PyType getElementType(PyExpression index, TypeEvalContext context) {
    final Object value = PyConstantExpressionEvaluator.evaluate(index);
    if (value instanceof Integer) {
      int iIndex = ((Integer)value).intValue();
      if (iIndex >= 0 && iIndex < myElementTypes.length) {
        return myElementTypes[iIndex];
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    PyTupleType that = (PyTupleType)o;

    if (!Arrays.equals(myElementTypes, that.myElementTypes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myElementTypes != null ? Arrays.hashCode(myElementTypes) : 0);
    return result;
  }
}

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

  public PyTupleType(PsiElement anchor, PyType[] elementTypes) {
    super(PyBuiltinCache.getInstance(anchor).getClass("tuple"), false);
    myElementTypes = elementTypes;
  }

  public PyTupleType(PyTupleType origin, PyType[] elementTypes) {
    super(origin.getPyClass(), false);
    myElementTypes = elementTypes;
  }

  public String getName() {
    return "tuple(" + StringUtil.join(myElementTypes, new Function<PyType, String>() {
      public String fun(PyType pyType) {
        return pyType == null ? "unknown" : pyType.getName();
      }
    }, ",") + ")";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return true;
  }

  public PyType getElementType(PyExpression index, TypeEvalContext context) {
    final Object value = PyConstantExpressionEvaluator.evaluate(index);
    if (value instanceof Integer) {
      return getElementType(((Integer)value).intValue());
    }
    return null;
  }

  public PyType getElementType(int index) {
    if (index >= 0 && index < myElementTypes.length) {
      return myElementTypes[index];
    }
    return null;
  }

  public int getElementCount() {
    return myElementTypes.length;
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

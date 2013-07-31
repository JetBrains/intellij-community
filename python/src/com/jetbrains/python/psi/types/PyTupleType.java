package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author yole
 */
public class PyTupleType extends PyClassTypeImpl implements PySubscriptableType {
  private final PyType[] myElementTypes;

  PyTupleType(@NotNull PyClass tupleClass, PsiElement anchor, PyType[] elementTypes) {
    super(tupleClass, false);
    myElementTypes = elementTypes;
  }

  @Nullable
  public static PyTupleType create(PsiElement anchor, PyType[] elementTypes) {
    PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, anchor, elementTypes);
    }
    return null;
  }

  public PyTupleType(PyTupleType origin, PyType[] elementTypes) {
    super(origin.getPyClass(), false);
    myElementTypes = elementTypes;
  }

  public String getName() {
    return "(" + StringUtil.join(myElementTypes, new Function<PyType, String>() {
      @Nullable
      public String fun(PyType pyType) {
        return pyType == null ? PyNames.UNKNOWN_TYPE : pyType.getName();
      }
    }, ", ") + ")";
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

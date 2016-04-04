/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
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
  private final boolean myHomogeneous;

  @Nullable
  public static PyTupleType create(@NotNull PsiElement anchor, @NotNull PyType[] elementTypes) {
    PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, elementTypes, false);
    }
    return null;
  }

  @Nullable
  public static PyTupleType createHomogeneous(@NotNull PsiElement anchor, @Nullable PyType elementType) {
    PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, new PyType[] {elementType}, true);
    }
    return null;
  }

  PyTupleType(@NotNull PyClass tupleClass, @NotNull PyType[] elementTypes, boolean homogeneous) {
    super(tupleClass, false);
    myElementTypes = elementTypes;
    myHomogeneous = homogeneous;
  }

  public PyTupleType(@NotNull PyTupleType origin, @NotNull PyType[] elementTypes) {
    this(origin.getPyClass(), elementTypes, false);
  }

  public String getName() {
    if (myHomogeneous) {
      return "(" + (getTypeName(myElementTypes[0])) + ", ...)";
    }
    return "(" + StringUtil.join(myElementTypes, PyTupleType::getTypeName, ", ") + ")";
  }

  @Nullable
  private static String getTypeName(@Nullable PyType type) {
    return type == null ? PyNames.UNKNOWN_TYPE : type.getName();
  }

  @Override
  public boolean isBuiltin() {
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
    if (myHomogeneous) {
      return myElementTypes[0];
    }
    if (index >= 0 && index < myElementTypes.length) {
      return myElementTypes[index];
    }
    return null;
  }

  public int getElementCount() {
    return myHomogeneous ? -1 : myElementTypes.length;
  }

  public boolean isHomogeneous() {
    return myHomogeneous;
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

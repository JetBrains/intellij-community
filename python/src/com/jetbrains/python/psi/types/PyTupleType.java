// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyTupleType extends PyClassTypeImpl implements PyCollectionType {

  @NotNull
  private final List<PyType> myElementTypes;
  private final boolean myHomogeneous;

  @Nullable
  public static PyTupleType create(@NotNull PsiElement anchor, @NotNull List<PyType> elementTypes) {
    final PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, elementTypes, false);
    }
    return null;
  }

  @Nullable
  public static PyTupleType createHomogeneous(@NotNull PsiElement anchor, @Nullable PyType elementType) {
    final PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, Collections.singletonList(elementType), true);
    }
    return null;
  }

  public PyTupleType(@NotNull PyClass tupleClass, @NotNull List<PyType> elementTypes, boolean homogeneous) {
    this(tupleClass, elementTypes, homogeneous, false);
  }

  protected PyTupleType(@NotNull PyClass tupleClass, @NotNull List<PyType> elementTypes, boolean homogeneous, boolean isDefinition) {
    super(tupleClass, isDefinition);
    myElementTypes = elementTypes;
    myHomogeneous = homogeneous;
  }

  @Override
  @NotNull
  public String getName() {
    if (myHomogeneous) {
      return "(" + (getTypeName(getIteratedItemType())) + ", ...)";
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

  /**
   * Access elements by zero-based index.
   *
   * @param index an index of item
   * @return type of item
   */
  @Nullable
  public PyType getElementType(int index) {
    if (myHomogeneous) {
      return getIteratedItemType();
    }
    if (index >= 0 && index < myElementTypes.size()) {
      return myElementTypes.get(index);
    }
    return null;
  }

  public int getElementCount() {
    return myHomogeneous ? -1 : myElementTypes.size();
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

    if (!myElementTypes.equals(that.myElementTypes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myElementTypes.hashCode();
    return result;
  }

  @NotNull
  @Override
  public List<PyType> getElementTypes() {
    return myElementTypes;
  }

  @Nullable
  @Override
  public PyType getIteratedItemType() {
    return PyUnionType.union(myElementTypes);
  }
}

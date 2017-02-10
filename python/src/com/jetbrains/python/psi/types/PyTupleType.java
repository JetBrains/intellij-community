/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    super(tupleClass, false);
    myElementTypes = elementTypes;
    myHomogeneous = homogeneous;
  }

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
  public List<PyType> getElementTypes(@NotNull TypeEvalContext context) {
    return myElementTypes;
  }

  @Nullable
  @Override
  public PyType getIteratedItemType() {
    return PyUnionType.union(myElementTypes);
  }
}

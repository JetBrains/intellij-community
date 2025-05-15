// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class PyTupleType extends PyClassTypeImpl implements PyCollectionType {

  private final PyUnpackedTupleType myUnpackedTupleType;

  public static @Nullable PyTupleType create(@NotNull PsiElement anchor, @NotNull List<? extends PyType> elementTypes) {
    final PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, elementTypes, false);
    }
    return null;
  }

  public static @Nullable PyTupleType createHomogeneous(@NotNull PsiElement anchor, @Nullable PyType elementType) {
    final PyClass tuple = PyBuiltinCache.getInstance(anchor).getClass(PyNames.TUPLE);
    if (tuple != null) {
      return new PyTupleType(tuple, Collections.singletonList(elementType), true);
    }
    return null;
  }

  public PyTupleType(@NotNull PyClass tupleClass, @NotNull List<? extends PyType> elementTypes, boolean homogeneous) {
    this(tupleClass, elementTypes, homogeneous, false);
  }

  protected PyTupleType(@NotNull PyClass tupleClass, @NotNull List<? extends PyType> elementTypes, boolean homogeneous, boolean isDefinition) {
    super(tupleClass, isDefinition);
    myUnpackedTupleType = new PyUnpackedTupleTypeImpl(elementTypes, homogeneous);
  }

  @Override
  public @NotNull String getName() {
    if (myUnpackedTupleType.isUnbound()) {
      return "(" + (getTypeName(getIteratedItemType())) + ", ...)";
    }
    return "(" + StringUtil.join(myUnpackedTupleType.getElementTypes(), PyTupleType::getTypeName, ", ") + ")";
  }

  private static @Nullable String getTypeName(@Nullable PyType type) {
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
  public @Nullable PyType getElementType(int index) {
    if (isHomogeneous()) {
      return getIteratedItemType();
    }
    List<PyType> elementTypes = myUnpackedTupleType.getElementTypes();
    return index >= 0 && index < elementTypes.size() ? elementTypes.get(index) : null;
  }

  public int getElementCount() {
    return isHomogeneous() ? -1 : myUnpackedTupleType.getElementTypes().size();
  }

  public boolean isHomogeneous() {
    return myUnpackedTupleType.isUnbound();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PyTupleType type = (PyTupleType)o;
    return Objects.equals(myUnpackedTupleType, type.myUnpackedTupleType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myUnpackedTupleType);
  }

  @Override
  public @NotNull List<PyType> getElementTypes() {
    return myUnpackedTupleType.getElementTypes();
  }

  @Override
  public @Nullable PyType getIteratedItemType() {
    List<PyType> types = myUnpackedTupleType.getElementTypes();
    List<PyType> unpackedTypes = ContainerUtil.map(types, type -> {
        if (type instanceof PyUnpackedTupleType unpackedTupleType) {
          assert unpackedTupleType.isUnbound();
          return unpackedTupleType.getElementTypes().get(0);
        } else {
          return type;
        }
      });
    return PyUnionType.union(unpackedTypes);
  }

  public @NotNull PyUnpackedTupleType asUnpackedTupleType() {
    return myUnpackedTupleType;
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
      return visitorExt.visitPyTupleType(this);
    }
    return visitor.visitPyType(this);
  }
}

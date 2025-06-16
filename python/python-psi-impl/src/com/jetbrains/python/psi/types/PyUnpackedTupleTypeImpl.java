package com.jetbrains.python.psi.types;


import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class PyUnpackedTupleTypeImpl implements PyUnpackedTupleType {
  public static final PyUnpackedTupleType UNSPECIFIED = new PyUnpackedTupleTypeImpl(Collections.singletonList(null), true);

  private final List<PyType> myElementTypes;
  private final boolean myIsHomogeneous;

  public PyUnpackedTupleTypeImpl(@NotNull List<? extends PyType> elementTypes, boolean isUnbound) {
    if (isUnbound) {
      if (elementTypes.size() != 1) {
        throw new IllegalArgumentException("Unbounded unpacked tuple type can have only one type parameter");
      }
      if (elementTypes.get(0) instanceof PyPositionalVariadicType) {
        throw new IllegalArgumentException("Unbounded unpacked tuple type of a TypeVarTuple or another unpacked tuple type is now allowed");
      }
      myElementTypes = new ArrayList<>(elementTypes);
    } else {
      myElementTypes = unpackElementTypes(elementTypes).toList();
    }
    myIsHomogeneous = isUnbound;
  }

  private static @NotNull Stream<PyType> unpackElementTypes(@NotNull List<? extends PyType> types) {
    return types.stream().flatMap(type -> {
      if (type instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
        return unpackElementTypes(unpackedTupleType.getElementTypes());
      } else {
        return Stream.of(type);
      }
    });
  }

  public static @NotNull PyUnpackedTupleType create(@NotNull List<? extends PyType> elementTypes) {
    return new PyUnpackedTupleTypeImpl(elementTypes, false);
  }

  public static @NotNull PyUnpackedTupleType createUnbound(@Nullable PyType type) {
    return new PyUnpackedTupleTypeImpl(Collections.singletonList(type), true);
  }

  @Override
  public @NotNull String getName() {
    StringBuilder res = new StringBuilder("*tuple[");
    StringUtil.join(myElementTypes, type -> type != null ? type.getName() : "Any", ", ", res);
    if (isUnbound()) {
      res.append(", ...");
    }
    res.append("]");
    return res.toString();
  }

  @Override
  public @NotNull List<PyType> getElementTypes() {
    return Collections.unmodifiableList(myElementTypes);
  }

  @Override
  public boolean isUnbound() {
    return myIsHomogeneous;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyUnpackedTupleTypeImpl type = (PyUnpackedTupleTypeImpl)o;
    return myIsHomogeneous == type.myIsHomogeneous && Objects.equals(myElementTypes, type.myElementTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myElementTypes, myIsHomogeneous);
  }

  @Override
  public String toString() {
    return "PyUnpackedTupleType: " + getName();
  }

  public @Nullable PyTupleType asTupleType(@NotNull PsiElement anchor) {
    if (isUnbound()) {
      return PyTupleType.createHomogeneous(anchor, getElementTypes().get(0));
    }
    else {
      return PyTupleType.create(anchor, getElementTypes());
    }
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
      return visitorExt.visitPyUnpackedTupleType(this);
    }
    return visitor.visitPyType(this);
  }
}

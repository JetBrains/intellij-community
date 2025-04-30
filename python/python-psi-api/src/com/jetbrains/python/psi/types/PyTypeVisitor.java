// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Similarly to {@link com.intellij.psi.PsiElementVisitor}, implements double dispatching for the {@link PyType} hierarchy.
 * <p>
 * Because the "unknown" type is historically represented as {@code null} in the type system, {@code PyTypeVisitor.visitPyType(type, visitor)}
 * should be used instead of direct {@code type.acceptTypeVisitor(visitor)} to properly account for possible {@code null} values.
 * <p>
 * This class gives access only to the types declared in the <tt>intellij.python.psi</tt> module. 
 * Most actual implementations should extend {@link PyTypeVisitorExt}.
 * <p>
 * There are helper {@link PyRecursiveTypeVisitor} and {@link PyCloningTypeVisitor} for recursive type
 * traversal and deep cloning of a type respectively.
 *
 * @see PyRecursiveTypeVisitor
 * @see PyCloningTypeVisitor
 * @see PyType#acceptTypeVisitor(PyTypeVisitor)
 * @see #visit(PyType, PyTypeVisitor) 
 * @see #visitUnknownType()
 */
@ApiStatus.Experimental
public abstract class PyTypeVisitor<T> {
  /**
   * Use this method instead of {@link PyType#acceptTypeVisitor(PyTypeVisitor)} to take into account
   * "unknown" {@code null} types, for which {@link #visitUnknownType()} should be called.
   */
  public static <T> T visit(@Nullable PyType type, @NotNull PyTypeVisitor<T> visitor) {
    return type == null ? visitor.visitUnknownType() : type.acceptTypeVisitor(visitor);
  }

  public T visitPyType(@NotNull PyType type) {
    return null;
  }

  public T visitPyClassType(@NotNull PyClassType classType) {
    return visitPyClassLikeType(classType);
  }

  public T visitPyClassLikeType(@NotNull PyClassLikeType classLikeType) {
    // Don't treat PyClassLikeType as PyCallableType. It's usually not what a visitor's user expects.
    return visitPyType(classLikeType);
  }

  public T visitPyFunctionType(@NotNull PyFunctionType functionType) {
    return visitPyCallableType(functionType);
  }

  public T visitPyCallableType(@NotNull PyCallableType callableType) {
    return visitPyType(callableType);
  }

  public T visitPyTypeVarType(@NotNull PyTypeVarType typeVarType) {
    return visitPyTypeParameterType(typeVarType);
  }

  public T visitPyTypeVarTupleType(@NotNull PyTypeVarTupleType typeVarTupleType) {
    return visitPyTypeParameterType(typeVarTupleType);
  }

  public T visitPyTypeParameterType(@NotNull PyTypeParameterType typeParameterType) {
    return visitPyType(typeParameterType);
  }

  public T visitPyUnpackedTupleType(@NotNull PyUnpackedTupleType unpackedTupleType) {
    return visitPyType(unpackedTupleType);
  }

  public T visitPyCallableParameterListType(@NotNull PyCallableParameterListType callableParameterListType) {
    return visitPyType(callableParameterListType);
  }

  public T visitUnknownType() {
    return null;
  }
}

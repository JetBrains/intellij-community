package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

public abstract class PyTypeVisitorExt<T> extends PyTypeVisitor<T> {
  public T visitPyLiteralType(@NotNull PyLiteralType literalType) {
    return visitPyClassType(literalType);
  }

  public T visitPyLiteralStringType(@NotNull PyLiteralStringType literalStringType) {
    return visitPyClassType(literalStringType);
  }

  public T visitPyModuleType(@NotNull PyModuleType moduleType) {
    return visitPyType(moduleType);
  }

  public T visitPyParamSpecType(@NotNull PyParamSpecType paramSpecType) {
    return visitPyTypeParameterType(paramSpecType);
  }

  public T visitPyGenericType(@NotNull PyCollectionType genericType) {
    return visitPyClassType(genericType);
  }

  public T visitPyTupleType(@NotNull PyTupleType tupleType) {
    return visitPyGenericType(tupleType);
  }

  public T visitPyNamedTupleType(@NotNull PyNamedTupleType namedTupleType) {
    return visitPyClassType(namedTupleType);
  }

  public T visitPySelfType(@NotNull PySelfType selfType) {
    return visitPyTypeParameterType(selfType);
  }

  public T visitPyTypedDictType(@NotNull PyTypedDictType typedDictType) {
    return visitPyClassType(typedDictType);
  }

  public T visitPyUnionType(@NotNull PyUnionType unionType) {
    return visitPyType(unionType);
  }

  public T visitPyUnsafeUnionType(@NotNull PyUnsafeUnionType unsafeUnionType) {
    return visitPyType(unsafeUnionType);
  }

  public T visitPyTypingNewType(@NotNull PyTypingNewType typingNewType) {
    return visitPyClassType(typingNewType);
  }

  public T visitPyNarrowedType(@NotNull PyNarrowedType narrowedType) {
    return visitPyType(narrowedType);
  }

  public T visitPyConcatenateType(@NotNull PyConcatenateType concatenateType) {
    return visitPyType(concatenateType);
  }
}

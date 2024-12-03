package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a type parameter substituted with a parameter list of a callable as described in
 * <a href="https://peps.python.org/pep-0612/">PEP 612 – Parameter Specification Variables</a>.
 * <p>
 * Declared with either {@code typing.ParamSpec} instantiation or {@code **P} syntax.
 * Concrete instantiations of such a parameter are either {@link PyCallableParameterListType} or {@link PyConcatenateType}.
 *
 * @see PyCallableParameterListType
 * @see PyConcatenateType
 */
public final class PyParamSpecType implements PyTypeParameterType, PyCallableParameterVariadicType {
  private final @NotNull String myName;
  private final @Nullable PyQualifiedNameOwner myDeclarationElement;
  private final @Nullable Ref<PyCallableParameterVariadicType> myDefaultType;
  private final @Nullable PyQualifiedNameOwner myScopeOwner;

  public PyParamSpecType(@NotNull String name) {
    this(name, null, null, null);
  }

  private PyParamSpecType(@NotNull String name,
                          @Nullable PyQualifiedNameOwner declarationElement,
                          @Nullable Ref<PyCallableParameterVariadicType> defaultType,
                          @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myDeclarationElement = declarationElement;
    myDefaultType = defaultType;
    myScopeOwner = scopeOwner;
  }

  public @NotNull PyParamSpecType withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
    return new PyParamSpecType(myName, declarationElement, myDefaultType, myScopeOwner);
  }

  public @NotNull PyParamSpecType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyParamSpecType(myName, myDeclarationElement, myDefaultType, scopeOwner);
  }

  public @NotNull PyParamSpecType withDefaultType(@Nullable Ref<PyCallableParameterVariadicType> defaultType) {
    return new PyParamSpecType(myName, myDeclarationElement, defaultType, myScopeOwner);
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myDeclarationElement;
  }

  @Override
  public @NotNull String getName() {
    return "**" + myName;
  }

  @Override
  public String toString() {
    String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
    return "PyParamSpecType: " + (scopeName != null ? scopeName + ":" : "") + myName;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getScopeOwner() {
    return myScopeOwner;
  }

  @Override
  public @Nullable Ref<PyCallableParameterVariadicType> getDefaultType() {
    return myDefaultType;
  }

  public @NotNull String getVariableName() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PyParamSpecType type = (PyParamSpecType)o;
    return myName.equals(type.myName) && Objects.equals(myScopeOwner, type.myScopeOwner);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}

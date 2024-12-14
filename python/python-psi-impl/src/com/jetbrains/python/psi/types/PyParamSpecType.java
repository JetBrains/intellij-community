package com.jetbrains.python.psi.types;

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
  @NotNull private final String myName;
  @Nullable private final PyQualifiedNameOwner myDeclarationElement;
  @Nullable private final PyCallableParameterVariadicType myDefaultType;
  @Nullable private final PyQualifiedNameOwner myScopeOwner;

  public PyParamSpecType(@NotNull String name) {
    this(name, null, null, null);
  }

  private PyParamSpecType(@NotNull String name,
                          @Nullable PyQualifiedNameOwner declarationElement,
                          @Nullable PyCallableParameterVariadicType defaultType,
                          @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myDeclarationElement = declarationElement;
    myDefaultType = defaultType;
    myScopeOwner = scopeOwner;
  }

  @NotNull
  public PyParamSpecType withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
    return new PyParamSpecType(myName, declarationElement, myDefaultType, myScopeOwner);
  }

  @NotNull
  public PyParamSpecType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyParamSpecType(myName, myDeclarationElement, myDefaultType, scopeOwner);
  }

  @NotNull
  public PyParamSpecType withDefaultType(@Nullable PyCallableParameterVariadicType defaultType) {
    return new PyParamSpecType(myName, myDeclarationElement, defaultType, myScopeOwner);
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myDeclarationElement;
  }

  @NotNull
  @Override
  public String getName() {
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
  @Nullable
  public PyCallableParameterVariadicType getDefaultType() {
    return myDefaultType;
  }

  @NotNull
  public String getVariableName() {
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

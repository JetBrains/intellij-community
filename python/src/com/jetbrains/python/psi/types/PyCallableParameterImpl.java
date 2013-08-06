package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyCallableParameterImpl implements PyCallableParameter {
  @Nullable private final String myName;
  @Nullable private final PyType myType;
  @Nullable private final PyParameter myElement;

  public PyCallableParameterImpl(@Nullable String name, @Nullable PyType type) {
    myName = name;
    myType = type;
    myElement = null;
  }

  public PyCallableParameterImpl(@Nullable PyParameter element) {
    myName = null;
    myType = null;
    myElement = element;
  }

  @Nullable
  @Override
  public String getName() {
    if (myName != null) {
      return myName;
    }
    else if (myElement != null) {
      return myElement.getName();
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context) {
    if (myType != null) {
      return myType;
    }
    else if (myElement instanceof PyNamedParameter) {
      return context.getType((PyNamedParameter)myElement);
    }
    return null;
  }

  @Nullable
  @Override
  public PyParameter getParameter() {
    return myElement;
  }
}

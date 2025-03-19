// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.changeSignature.MethodDescriptor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */

public class PyMethodDescriptor implements MethodDescriptor<PyParameterInfo, String> {
  private final PyFunction myFunction;

  public PyMethodDescriptor(@NotNull PyFunction function) {
    myFunction = function;
  }

  @Override
  public String getName() {
    return myFunction.getName();
  }

  @Override
  public @NotNull List<PyParameterInfo> getParameters() {
    List<PyParameterInfo> parameterInfos = new ArrayList<>();
    PyParameter[] parameters = myFunction.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PyParameter parameter = parameters[i];
      final PyExpression defaultValue = parameter.getDefaultValue();
      final String name;
      if (parameter instanceof PyNamedParameter) {
        name = ParamHelper.getNameInSignature((PyNamedParameter)parameter);
      }
      else {
        name = parameter.getText();
      }
      parameterInfos.add(new PyParameterInfo(i, name, defaultValue == null ? null : defaultValue.getText(),
                                             defaultValue != null && !StringUtil.isEmptyOrSpaces(defaultValue.getText())));
    }
    return parameterInfos;
  }

  @Override
  public int getParametersCount() {
    return myFunction.getParameterList().getParameters().length;
  }

  @Override
  public @NotNull String getVisibility() {
    return "";
  }

  @Override
  public @NotNull PyFunction getMethod() {
    return myFunction;
  }

  @Override
  public boolean canChangeVisibility() {
    return false;
  }

  @Override
  public boolean canChangeParameters() {
    return true;
  }

  @Override
  public boolean canChangeName() {
    return true;
  }

  @Override
  public @NotNull ReadWriteOption canChangeReturnType() {
    return ReadWriteOption.None;
  }
}

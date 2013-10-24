/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.changeSignature.MethodDescriptor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */

public class PyMethodDescriptor implements MethodDescriptor<PyParameterInfo, String> {
  private final PyFunction myFunction;

  public PyMethodDescriptor(PyFunction function) {
    myFunction = function;
  }

  @Override
  public String getName() {
    return myFunction.getName();
  }

  @Override
  public List<PyParameterInfo> getParameters() {
    List<PyParameterInfo> parameterInfos = new ArrayList<PyParameterInfo>();
    PyParameter[] parameters = myFunction.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PyParameter parameter = parameters[i];
      final PyExpression defaultValue = parameter.getDefaultValue();
      String name = parameter instanceof PySingleStarParameter || parameter instanceof PyTupleParameter ||
                    ((PyNamedParameterImpl)parameter).isPositionalContainer() ||
                    ((PyNamedParameterImpl)parameter).isKeywordContainer()? parameter.getText() : parameter.getName();
      parameterInfos.add(new PyParameterInfo(i, name, defaultValue == null? null : defaultValue.getText(),
                                             defaultValue != null && !StringUtil.isEmptyOrSpaces(defaultValue.getText())));
    }
    return parameterInfos;
  }

  @Override
  public int getParametersCount() {
    return myFunction.getParameterList().getParameters().length;
  }

  @Override
  public String getVisibility() {
    return "";
  }

  @Override
  public PyFunction getMethod() {
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
  public ReadWriteOption canChangeReturnType() {
    return ReadWriteOption.None;
  }
}

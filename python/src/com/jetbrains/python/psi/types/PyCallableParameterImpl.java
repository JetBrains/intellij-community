/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

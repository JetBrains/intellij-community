/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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

  public PyCallableParameterImpl(@NotNull PyParameter element) {
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

  @Nullable
  @Override
  public PyExpression getDefaultValue() {
    return myElement == null ? null : myElement.getDefaultValue();
  }

  @Override
  public boolean hasDefaultValue() {
    return myElement != null && myElement.hasDefaultValue();
  }

  @Override
  public boolean isPositionalContainer() {
    final PyNamedParameter namedParameter = PyUtil.as(myElement, PyNamedParameter.class);
    return namedParameter != null && namedParameter.isPositionalContainer();
  }

  @Override
  public boolean isKeywordContainer() {
    final PyNamedParameter namedParameter = PyUtil.as(myElement, PyNamedParameter.class);
    return namedParameter != null && namedParameter.isKeywordContainer();
  }

  @NotNull
  @Override
  public String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context) {
    if (myElement instanceof PyNamedParameter || myElement == null) {
      final StringBuilder sb = new StringBuilder();

      if (isPositionalContainer()) sb.append("*");
      else if (isKeywordContainer()) sb.append("**");

      final String name = getName();
      sb.append(name != null ? name : "...");

      final PyType argumentType = context == null ? null : getArgumentType(context);
      if (argumentType != null) {
        sb.append(": ");
        sb.append(PythonDocumentationProvider.getTypeDescription(argumentType, context));
      }

      final PyExpression defaultValue = getDefaultValue();
      if (defaultValueShouldBeIncluded(includeDefaultValue, defaultValue, argumentType)) {
        final Pair<String, String> quotes = defaultValue instanceof PyStringLiteralExpression
                                            ? PyStringLiteralUtil.getQuotes(defaultValue.getText())
                                            : null;

        sb.append("=");
        if (quotes != null) {
          final String value = ((PyStringLiteralExpression)defaultValue).getStringValue();
          sb.append(quotes.getFirst());
          StringUtil.escapeStringCharacters(value.length(), value, sb);
          sb.append(quotes.getSecond());
        }
        else {
          sb.append(PyUtil.getReadableRepr(defaultValue, true));
        }
      }

      return sb.toString();
    }

    return PyUtil.getReadableRepr(myElement, false);
  }

  @Nullable
  @Override
  public PyType getArgumentType(@NotNull TypeEvalContext context) {
    final PyType parameterType = getType(context);

    if (parameterType instanceof PyCollectionType) {
      final PyCollectionType collectionType = (PyCollectionType)parameterType;

      if (isPositionalContainer()) {
        return collectionType.getIteratedItemType();
      }
      else if (isKeywordContainer()) {
        return ContainerUtil.getOrElse(collectionType.getElementTypes(context), 1, null);
      }
    }

    return parameterType;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PyCallableParameterImpl parameter = (PyCallableParameterImpl)o;
    return Objects.equals(myName, parameter.myName) &&
           Objects.equals(myType, parameter.myType) &&
           Objects.equals(myElement, parameter.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myType, myElement);
  }

  private static boolean defaultValueShouldBeIncluded(boolean includeDefaultValue,
                                                      @Nullable PyExpression defaultValue,
                                                      @Nullable PyType type) {
    if (!includeDefaultValue || defaultValue == null) return false;

    // In case of `None` default value, it will be listed in the type as `Optional[...]` or `Union[..., None, ...]`
    return type == null || !PyNames.NONE.equals(defaultValue.getText());
  }
}

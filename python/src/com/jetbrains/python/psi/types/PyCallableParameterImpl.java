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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
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
  @Nullable private final Ref<PyType> myType;
  @Nullable private final PyExpression myDefaultValue;
  @Nullable private final PyParameter myElement;

  private PyCallableParameterImpl(@Nullable String name,
                                  @Nullable Ref<PyType> type,
                                  @Nullable PyExpression defaultValue,
                                  @Nullable PyParameter element) {
    myName = name;
    myType = type;
    myDefaultValue = defaultValue;
    myElement = element;
  }

  @NotNull
  public static PyCallableParameter nonPsi(@Nullable PyType type) {
    return nonPsi(null, type);
  }

  @NotNull
  public static PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type) {
    return nonPsi(name, type, null);
  }

  @NotNull
  public static PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type, @Nullable PyExpression defaultValue) {
    return new PyCallableParameterImpl(name, Ref.create(type), defaultValue, null);
  }

  @NotNull
  public static PyCallableParameter psi(@NotNull PyParameter parameter) {
    return new PyCallableParameterImpl(null, null, null, parameter);
  }

  @NotNull
  public static PyCallableParameter psi(@NotNull PyParameter parameter, @Nullable PyType type) {
    return new PyCallableParameterImpl(null, Ref.create(type), null, parameter);
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
      return myType.get();
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
    return myElement == null ? myDefaultValue : myElement.getDefaultValue();
  }

  @Override
  public boolean hasDefaultValue() {
    return myElement == null ? myDefaultValue != null : myElement.hasDefaultValue();
  }

  @Nullable
  @Override
  public String getDefaultValueText() {
    if (myElement != null) return myElement.getDefaultValueText();
    return myDefaultValue == null ? null : myDefaultValue.getText();
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

  @Override
  public boolean isSelf() {
    final PyParameter parameter = PyUtil.as(myElement, PyParameter.class);
    return parameter != null && parameter.isSelf();
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

      final String defaultValue = getDefaultValueText();
      if (includeDefaultValue && defaultValue != null) {
        final Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(defaultValue);

        sb.append("=");
        if (quotes != null) {
          final String value = defaultValue.substring(quotes.getFirst().length(), defaultValue.length() - quotes.getSecond().length());
          sb.append(quotes.getFirst());
          StringUtil.escapeStringCharacters(value.length(), value, sb);
          sb.append(quotes.getSecond());
        }
        else {
          sb.append(defaultValue);
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
           Objects.equals(Ref.deref(myType), Ref.deref(parameter.myType)) &&
           Objects.equals(myElement, parameter.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, Ref.deref(myType), myElement);
  }
}

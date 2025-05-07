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

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class PyCallableParameterImpl implements PyCallableParameter {
  private final @Nullable @NlsSafe String myName;
  private final @Nullable Ref<PyType> myType;
  private final @Nullable PyExpression myDefaultValue;
  private final @Nullable PyParameter myElement;
  private final @Nullable PsiElement myDeclarationElement;
  private final boolean myIsPositional;
  private final boolean myIsKeyword;

  PyCallableParameterImpl(@Nullable String name,
                          @Nullable Ref<PyType> type,
                          @Nullable PyExpression defaultValue,
                          @Nullable PyParameter element,
                          boolean isPositional,
                          boolean isKeyword,
                          @Nullable PsiElement declarationElement) {
    myName = name;
    myType = type;
    myDefaultValue = defaultValue;
    myElement = element;
    myIsPositional = isPositional;
    myIsKeyword = isKeyword;
    myDeclarationElement = declarationElement;
  }

  public static @NotNull PyCallableParameter nonPsi(@Nullable PyType type) {
    return nonPsi(null, type);
  }

  public static @NotNull PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type) {
    return nonPsi(name, type, null);
  }

  public static @NotNull PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type, @Nullable PyExpression defaultValue) {
    return new PyCallableParameterImpl(name, Ref.create(type), defaultValue, null, false, false, null);
  }

  public static @NotNull PyCallableParameter nonPsi(@Nullable String name, @Nullable PyType type, @Nullable PyExpression defaultValue,
                                                    @NotNull PsiElement declarationElement) {
    return new PyCallableParameterImpl(name, Ref.create(type), defaultValue, null, false, false, declarationElement);
  }

  public static @NotNull PyCallableParameter positionalNonPsi(@Nullable String name, @Nullable PyType type) {
    return new PyCallableParameterImpl(name, Ref.create(type), null, null, true, false, null);
  }

  public static @NotNull PyCallableParameter keywordNonPsi(@Nullable String name, @Nullable PyType type) {
    return new PyCallableParameterImpl(name, Ref.create(type), null, null, false, true, null);
  }

  public static @NotNull PyCallableParameter psi(@NotNull PyParameter parameter) {
    return new PyCallableParameterImpl(null, null, null, parameter, false, false, null);
  }

  public static @NotNull PyCallableParameter psi(@NotNull PyParameter parameter, @Nullable PyType type) {
    return new PyCallableParameterImpl(null, Ref.create(type), null, parameter, false, false, null);
  }

  @Override
  public @Nullable @Nls String getName() {
    if (myName != null) {
      return myName;
    }
    else if (myElement != null) {
      return myElement.getName();
    }
    return null;
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context) {
    if (myType != null) {
      return myType.get();
    }
    else if (myElement instanceof PyNamedParameter) {
      return context.getType((PyNamedParameter)myElement);
    }
    return null;
  }

  @Override
  public @Nullable PyParameter getParameter() {
    return myElement;
  }

  @Override
  public @Nullable PsiElement getDeclarationElement() {
    if (myDeclarationElement != null) return myDeclarationElement;
    return getParameter();
  }

  @Override
  public @Nullable PyExpression getDefaultValue() {
    return myElement == null ? myDefaultValue : myElement.getDefaultValue();
  }

  @Override
  public boolean hasDefaultValue() {
    return myElement == null ? myDefaultValue != null : myElement.hasDefaultValue();
  }

  @Override
  public @Nullable String getDefaultValueText() {
    if (myElement != null) return myElement.getDefaultValueText();
    return myDefaultValue == null ? null : myDefaultValue.getText();
  }

  @Override
  public boolean isPositionalContainer() {
    if (myIsPositional) return true;

    final PyNamedParameter namedParameter = PyUtil.as(myElement, PyNamedParameter.class);
    return namedParameter != null && namedParameter.isPositionalContainer();
  }

  @Override
  public boolean isKeywordContainer() {
    if (myIsKeyword) return true;

    final PyNamedParameter namedParameter = PyUtil.as(myElement, PyNamedParameter.class);
    return namedParameter != null && namedParameter.isKeywordContainer();
  }

  @Override
  public boolean isSelf() {
    return myElement != null && myElement.isSelf();
  }

  @Override
  public @NotNull String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context) {
    return getPresentableText(includeDefaultValue, context, Objects::isNull);
  }

  @Override
  public @NotNull String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context, @NotNull Predicate<PyType> typeFilter) {
    if (myElement instanceof PyNamedParameter || myElement == null) {
      final StringBuilder sb = new StringBuilder();

      sb.append(ParamHelper.getNameInSignature(this));

      boolean renderedAsTyped = false;
      if (context != null) {
        final PyType argumentType = getArgumentType(context);
        if (!typeFilter.test(argumentType)) {
          sb.append(": ");
          sb.append(PythonDocumentationProvider.getTypeName(argumentType, context));
          renderedAsTyped = true;
        }
      }

      if (includeDefaultValue) {
        sb.append(ObjectUtils.notNull(ParamHelper.getDefaultValuePartInSignature(getDefaultValueText(), renderedAsTyped), ""));
      }

      return sb.toString();
    }

    return PyUtil.getReadableRepr(myElement, false);
  }

  @Override
  public @Nullable PyType getArgumentType(@NotNull TypeEvalContext context) {
    final PyType parameterType = getType(context);

    if (isPositionalContainer() && parameterType instanceof PyTupleType tupleType) {
      // *args: str is equivalent to *args: *tuple[str, ...]
      // *args: *Ts is equivalent to *args: *tuple[*Ts]
      // Convert its type to a more general form of an unpacked tuple
      PyUnpackedTupleType unpackedTupleType = tupleType.asUnpackedTupleType();
      if (unpackedTupleType.isUnbound()) {
        return unpackedTupleType.getElementTypes().get(0);
      }
      return unpackedTupleType;
    }
    else if (isKeywordContainer() && parameterType instanceof PyCollectionType dictType) {
      return ContainerUtil.getOrElse(dictType.getElementTypes(), 1, null);
    }

    return parameterType;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PyCallableParameterImpl parameter = (PyCallableParameterImpl)o;
    return myIsPositional == parameter.myIsPositional &&
           myIsKeyword == parameter.myIsKeyword &&
           Objects.equals(myName, parameter.myName) &&
           Objects.equals(Ref.deref(myType), Ref.deref(parameter.myType)) &&
           Objects.equals(myDefaultValue, parameter.myDefaultValue) &&
           Objects.equals(myElement, parameter.myElement);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, Ref.deref(myType), myDefaultValue, myElement, myIsPositional, myIsKeyword);
  }
}

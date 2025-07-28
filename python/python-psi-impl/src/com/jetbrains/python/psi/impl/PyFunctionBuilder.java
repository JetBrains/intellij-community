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
package com.jetbrains.python.psi.impl;

import com.google.common.base.Strings;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class PyFunctionBuilder {
  private final String myName;
  private final List<String> myParameters = new ArrayList<>();
  private final Map<String, String> myParameterTypes = new HashMap<>();
  private final List<String> myStatements = new ArrayList<>();
  private final List<String> myDecorators = new ArrayList<>();
  private final PsiElement mySettingAnchor;
  private final @NotNull Map<String, String> myDecoratorValues = new HashMap<>();
  private String myReturnType;
  private boolean myAsync = false;
  private PyDocstringGenerator myDocStringGenerator;

  /**
   * Creates builder copying signature and doc from another one.
   *
   * @param source                  what to copy
   * @param decoratorsToCopyIfExist list of decorator names to be copied to new function.
   * @return builder configured by this function
   */
  public static @NotNull PyFunctionBuilder copySignature(final @NotNull PyFunction source, final String @NotNull ... decoratorsToCopyIfExist) {
    final String name = source.getName();
    final PyFunctionBuilder functionBuilder = new PyFunctionBuilder((name != null) ? name : "", source);
    for (final PyParameter parameter : source.getParameterList().getParameters()) {
      final String parameterName = parameter.getName();
      if (parameterName != null) {
        functionBuilder.parameter(parameterName);
      }
    }
    final PyDecoratorList decoratorList = source.getDecoratorList();
    if (decoratorList != null) {
      for (final PyDecorator decorator : decoratorList.getDecorators()) {
        final String decoratorName = decorator.getName();
        if (decoratorName != null) {
          if (ArrayUtil.contains(decoratorName, decoratorsToCopyIfExist)) {
            functionBuilder.decorate(decoratorName);
          }
        }
      }
    }
    functionBuilder.myDocStringGenerator = PyDocstringGenerator.forDocStringOwner(source);
    return functionBuilder;
  }

  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where the generated function is going to be inserted.
   *                       It's necessary to detect configured docstring format and Python indentation size and, as a result,
   *                       generate a properly formatted docstring.
   */
  public PyFunctionBuilder(@NotNull String name, @NotNull PsiElement settingsAnchor) {
    myName = name;
    myDocStringGenerator = PyDocstringGenerator.create(DocStringParser.getConfiguredDocStringFormatOrPlain(settingsAnchor),
                                                       PyIndentUtil.getIndentFromSettings(settingsAnchor.getContainingFile()),
                                                       settingsAnchor);
    mySettingAnchor = settingsAnchor;
  }

  /**
   * Adds param and its type to doc
   * @param name param name
   * @param type param type
   */
  public @NotNull PyFunctionBuilder parameterWithDocString(@NotNull String name, @NotNull String type) {
    parameter(name, type);
    myDocStringGenerator.withParamTypedByName(name, type);
    return this;
  }

  public @NotNull PyFunctionBuilder parameter(@NotNull String baseName) {
    return parameter(baseName, null);
  }

  public @NotNull PyFunctionBuilder parameter(@NotNull String baseName, @Nullable String type) {
    String name = baseName;
    int uniqueIndex = 0;
    while (myParameters.contains(name)) {
      uniqueIndex++;
      name = baseName + uniqueIndex;
    }
    myParameters.add(name);
    if (!Strings.isNullOrEmpty(type)) {
      myParameterTypes.put(name, type);
    }
    return this;
  }

  public @NotNull PyFunctionBuilder returnType(String returnType) {
    myReturnType = returnType;
    return this;
  }

  public @NotNull PyFunctionBuilder makeAsync() {
    myAsync = true;
    return this;
  }

  public @NotNull PyFunctionBuilder statement(String text) {
    myStatements.add(text);
    return this;
  }

  public @NotNull PyFunction addFunction(PsiElement target) {
    return (PyFunction)target.add(buildFunction());
  }

  public @NotNull PyFunction addFunctionAfter(PsiElement target, PsiElement anchor) {
    return (PyFunction)target.addAfter(buildFunction(), anchor);
  }

  public @NotNull PyFunction buildFunction() {
    PyElementGenerator generator = PyElementGenerator.getInstance(mySettingAnchor.getProject());
    return generator.createFromText(LanguageLevel.forElement(mySettingAnchor), PyFunction.class, buildText(generator));
  }

  private @NotNull String buildText(PyElementGenerator generator) {
    StringBuilder builder = new StringBuilder();
    for (String decorator : myDecorators) {
      final StringBuilder decoratorAppender = builder.append('@' + decorator);
      if (myDecoratorValues.containsKey(decorator)) {
        final PyCallExpression fakeCall = generator.createCallExpression(LanguageLevel.forElement(mySettingAnchor), "fakeFunction");
        fakeCall.getArgumentList().addArgument(generator.createStringLiteralFromString(myDecoratorValues.get(decorator)));
        decoratorAppender.append(fakeCall.getArgumentList().getText());
      }
      decoratorAppender.append("\n");
    }
    if (myAsync) {
      builder.append("async ");
    }
    List<Pair<@NotNull String, @Nullable String>> parameters =
      ContainerUtil.map(myParameters, paramName -> Pair.create(paramName, myParameterTypes.get(paramName)));

    appendMethodSignature(builder, myName, parameters, myReturnType);
    builder.append(":");
    List<String> statements = myStatements.isEmpty() ? Collections.singletonList(PyNames.PASS) : myStatements;

    final String indent = PyIndentUtil.getIndentFromSettings(mySettingAnchor.getContainingFile());
    // There was an original docstring or some parameters were added via parameterWithType()
    if (!myDocStringGenerator.isNewMode() || myDocStringGenerator.hasParametersToAdd()) {
      final String docstring = PyIndentUtil.changeIndent(myDocStringGenerator.buildDocString(), true, indent);
      builder.append('\n').append(indent).append(docstring);
    }
    for (String statement : statements) {
      builder.append('\n').append(indent).append(statement);
    }
    return builder.toString();
  }

  /**
   * Adds decorator with argument
   *
   * @param decoratorName decorator name
   * @param value         its argument
   */
  public void decorate(final @NotNull String decoratorName, final @NotNull String value) {
    decorate(decoratorName);
    myDecoratorValues.put(decoratorName, value);
  }

  public void decorate(String decoratorName) {
    myDecorators.add(decoratorName);
  }

  public static void appendMethodSignature(@NotNull StringBuilder builder, @NotNull String name,
                                           @NotNull List<Pair<@NotNull String, @Nullable String>> parameters,
                                           @Nullable String returnTypeName
  ) {
    builder.append("def ");
    builder.append(name);
    builder.append("(");
    for (int i = 0; i < parameters.size(); i++) {
      Pair<@NotNull String, @Nullable String> parameter = parameters.get(i);
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(parameter.first);
      if (parameter.second != null) {
        builder.append(": ");
        builder.append(parameter.second);
      }
    }
    builder.append(")");
    if (returnTypeName != null) {
      builder.append(" -> ");
      builder.append(returnTypeName);
      builder.append(" ");
    }
  }
}

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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyDocstringGenerator;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class PyFunctionBuilder {
  private final String myName;
  private final List<String> myParameters = new ArrayList<>();
  private final List<String> myStatements = new ArrayList<>();
  private final List<String> myDecorators = new ArrayList<>();
  private String myAnnotation = null;
  @NotNull
  private final Map<String, String> myDecoratorValues = new HashMap<>();
  private boolean myAsync = false;
  private PyDocstringGenerator myDocStringGenerator;

  /**
   * Creates builder copying signature and doc from another one.
   *
   * @param source                  what to copy
   * @param decoratorsToCopyIfExist list of decorator names to be copied to new function.
   * @return builder configured by this function
   */
  @NotNull
  public static PyFunctionBuilder copySignature(@NotNull final PyFunction source, @NotNull final String... decoratorsToCopyIfExist) {
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

  @Deprecated
  public PyFunctionBuilder(@NotNull String name) {
    myName = name;
    myDocStringGenerator = null;
  }

  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
   *                       It's needed to detect configured docstring format and Python indentation size and, as result, 
   *                       generate properly formatted docstring. 
   */
  public PyFunctionBuilder(@NotNull String name, @NotNull PsiElement settingsAnchor) {
    myName = name;
    myDocStringGenerator = PyDocstringGenerator.create(DocStringUtil.getConfiguredDocStringFormat(settingsAnchor), 
                                                       PyIndentUtil.getIndentFromSettings(settingsAnchor.getProject()), 
                                                       settingsAnchor);
  }

  /**
   * Adds param and its type to doc
   * @param format what docstyle to use to doc param type
   * @param name param name
   * @param type param type
   */
  @NotNull
  public PyFunctionBuilder parameterWithType(@NotNull String name, @NotNull String type) {
    parameter(name);
    myDocStringGenerator.withParamTypedByName(name, type);
    return this;
  }

  @NotNull
  @Deprecated
  public PyFunctionBuilder parameterWithType(@NotNull final String name,
                                             @NotNull final String type,
                                             @NotNull final DocStringFormat format) {
    parameter(name);
    myDocStringGenerator.withParamTypedByName(name, type);
    return this;
  }

  public PyFunctionBuilder parameter(String baseName) {
    String name = baseName;
    int uniqueIndex = 0;
    while (myParameters.contains(name)) {
      uniqueIndex++;
      name = baseName + uniqueIndex;
    }
    myParameters.add(name);
    return this;
  }

  public PyFunctionBuilder annotation(String text) {
    myAnnotation = text;
    return this;
  }

  public PyFunctionBuilder makeAsync() {
    myAsync = true;
    return this;
  }

  public PyFunctionBuilder statement(String text) {
    myStatements.add(text);
    return this;
  }

  public PyFunction addFunction(PsiElement target, final LanguageLevel languageLevel) {
    return (PyFunction)target.add(buildFunction(target.getProject(), languageLevel));
  }

  public PyFunction addFunctionAfter(PsiElement target, PsiElement anchor, final LanguageLevel languageLevel) {
    return (PyFunction)target.addAfter(buildFunction(target.getProject(), languageLevel), anchor);
  }

  public PyFunction buildFunction(Project project, final LanguageLevel languageLevel) {
    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    String text = buildText(project, generator, languageLevel);
    return generator.createFromText(languageLevel, PyFunction.class, text);
  }

  private String buildText(Project project, PyElementGenerator generator, LanguageLevel languageLevel) {
    StringBuilder builder = new StringBuilder();
    for (String decorator : myDecorators) {
      final StringBuilder decoratorAppender = builder.append('@' + decorator);
      if (myDecoratorValues.containsKey(decorator)) {
        final PyCallExpression fakeCall = generator.createCallExpression(languageLevel, "fakeFunction");
        fakeCall.getArgumentList().addArgument(generator.createStringLiteralFromString(myDecoratorValues.get(decorator)));
        decoratorAppender.append(fakeCall.getArgumentList().getText());
      }
      decoratorAppender.append("\n");
    }
    if (myAsync) {
      builder.append("async ");
    }
    builder.append("def ");
    builder.append(myName).append("(");
    builder.append(StringUtil.join(myParameters, ", "));
    builder.append(")");
    if (myAnnotation != null) {
      builder.append(myAnnotation);
    }
    builder.append(":");
    List<String> statements = myStatements.isEmpty() ? Collections.singletonList(PyNames.PASS) : myStatements;

    final String indent = PyIndentUtil.getIndentFromSettings(project);
    // There was original docstring or some parameters were added via parameterWithType()
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
  public void decorate(@NotNull final String decoratorName, @NotNull final String value) {
    decorate(decoratorName);
    myDecoratorValues.put(decoratorName, value);
  }

  public void decorate(String decoratorName) {
    myDecorators.add(decoratorName);
  }
}

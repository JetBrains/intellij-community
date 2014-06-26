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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PyFunctionBuilder {
  private static final String COMMENTS_BOUNDARY = "\"\"\"";
  private static final Pattern INDENT_REMOVE_PATTERN = Pattern.compile("^\\s+", Pattern.MULTILINE);
  private final String myName;
  private final List<String> myParameters = new ArrayList<String>();
  private final List<String> myStatements = new ArrayList<String>();
  private final List<String> myDecorators = new ArrayList<String>();
  private String myAnnotation = null;
  private String[] myDocStringLines = null;
  @NotNull
  private final Map<String, String> myDecoratorValues = new HashMap<String, String>();

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
    final PyFunctionBuilder functionBuilder = new PyFunctionBuilder((name != null) ? name : "");
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
    final String docString = source.getDocStringValue();
    if (docString != null) {
      functionBuilder.docString(docString);
    }
    return functionBuilder;
  }

  /**
   * Adds docstring to function. Provide doc with out of comment blocks.
   *
   * @param docString doc
   */
  public void docString(@NotNull final String docString) {
    myDocStringLines = StringUtil.splitByLines(removeIndent(docString));
  }

  @NotNull
  private String removeIndent(@NotNull final String string) {
    return INDENT_REMOVE_PATTERN.matcher(string).replaceAll("");
  }

  public PyFunctionBuilder(String name) {
    myName = name;
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
    builder.append("def ");
    builder.append(myName).append("(");
    builder.append(StringUtil.join(myParameters, ", "));
    builder.append(")");
    if (myAnnotation != null) {
      builder.append(myAnnotation);
    }
    builder.append(":");
    List<String> statements = myStatements.isEmpty() ? Collections.singletonList(PyNames.PASS) : myStatements;

    if (myDocStringLines != null) {
      final List<String> comments = new ArrayList<String>(myDocStringLines.length + 2);
      comments.add(COMMENTS_BOUNDARY);
      comments.addAll(Arrays.asList(myDocStringLines));
      comments.add(COMMENTS_BOUNDARY);
      statements = new ArrayList<String>(statements);
      statements.addAll(0, comments);
    }

    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    int indentSize = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE).INDENT_SIZE;
    String indent = StringUtil.repeatSymbol(' ', indentSize);
    for (String statement : statements) {
      builder.append("\n").append(indent).append(statement);
    }
    return builder.toString();
  }

  /**
   * Adds decorator with argument
   * @param decoratorName decorator name
   * @param value its argument
   */
  public void decorate(@NotNull final String decoratorName, @NotNull final String value) {
    decorate(decoratorName);
    myDecoratorValues.put(decoratorName, value);
  }

  public void decorate(String decoratorName) {
    myDecorators.add(decoratorName);
  }

  @NotNull
  private static String getIndent(@NotNull final Project project) {
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    final int indentSize = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE).INDENT_SIZE;
    return StringUtil.repeatSymbol(' ', indentSize);
  }
}

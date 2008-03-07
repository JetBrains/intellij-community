/*
 * Copyright 2005 Pythonid Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS"; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyElementGenerator {
  ASTNode createNameIdentifier(Project project, String name);

  /**
   * str must have quotes around it and be fully escaped.
   */
  PyStringLiteralExpression createStringLiteralAlreadyEscaped(
      Project project, String str);

  PyStringLiteralExpression createStringLiteralFromString(
      Project project, @NotNull PsiFile destination, String unescaped);

  PyListLiteralExpression createListLiteral(Project project);

  PyKeywordArgument createKeywordArgument(Project project, String keyword,
                                          @Nullable PyExpression expression);

  ASTNode createComma(Project project);

  PsiElement insertItemIntoList(Project project, PyElement list,
                                @Nullable PyExpression afterThis,
                                PyExpression toInsert)
      throws IncorrectOperationException;

  PyBinaryExpression createBinaryExpression(Project project, String s,
                                            PyExpression expr,
                                            PyExpression listLiteral);

  PyExpression createExpressionFromText(Project project, String text);

  PyCallExpression createCallExpression(Project project,
                                        String functionName);

  PyExpressionStatement createExpressionStatement(Project project,
                                                  PyExpression expr);

  void setStringValue(PyStringLiteralExpression string, String value);

  PyImportStatement createImportStatementFromText(Project project, String text);
}

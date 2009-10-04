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
  PyStringLiteralExpression createStringLiteralAlreadyEscaped( Project project, String str);

  /**
   * Creates a string literal, adding appropriate quotes, properly escaping characters inside.
   * @param project     where we are working
   * @param destination where the literal is destined to; used to determine the encoding.
   * @param unescaped   the string
   * @return            a newly created literal
   */
  PyStringLiteralExpression createStringLiteralFromString(Project project, @Nullable PsiFile destination, String unescaped);

  PyListLiteralExpression createListLiteral(Project project);

  PyKeywordArgument createKeywordArgument( Project project, String keyword, @Nullable PyExpression expression);

  ASTNode createComma(Project project);

  ASTNode createDot(Project project);

  PsiElement insertItemIntoList(
    Project project, PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert
  ) throws IncorrectOperationException;

  PyBinaryExpression createBinaryExpression( Project project, String s, PyExpression expr, PyExpression listLiteral);

  PyExpression createExpressionFromText(Project project, String text);

  PyCallExpression createCallExpression(Project project, String functionName);

  PyExpressionStatement createExpressionStatement(Project project, PyExpression expr);

  void setStringValue(PyStringLiteralExpression string, String value);

  PyImportStatement createImportStatementFromText(Project project, String text);

  <T> T createFromText(final Project project, Class<T> aClass, final String text);

  /**
   * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
   * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
   * @param project where we are working
   * @param aClass  class of the PSI element; may be an interface not descending from PsiElement, as long as target node can be cast to it
   * @param text    text to parse
   * @param path    a sequence of numbers, each telling which child to select at current tree level; 0 means first child, etc.
   * @return the newly created PSI element
   */
  <T> T createFromText(final Project project, Class<T> aClass, final String text, final int[] path);

  PyNamedParameter createParameter(@NotNull final Project project, @NotNull String name);
}

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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyElementGenerator {
  public static PyElementGenerator getInstance(Project project) {
    return ServiceManager.getService(project, PyElementGenerator.class);
  }

  public abstract ASTNode createNameIdentifier(String name, LanguageLevel languageLevel);

  /**
   * str must have quotes around it and be fully escaped.
   */
  public abstract PyStringLiteralExpression createStringLiteralAlreadyEscaped(String str);




  /**
   * Creates a string literal, adding appropriate quotes, properly escaping characters inside.
   * @param destination where the literal is destined to; used to determine the encoding.
   * @param unescaped   the string
   * @return            a newly created literal
   */
  public abstract PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination, String unescaped);
  public abstract PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped);

  public abstract PyStringLiteralExpression createStringLiteral(@NotNull PyStringLiteralExpression oldElement, @NotNull String unescaped);

  public abstract PyListLiteralExpression createListLiteral();

  public abstract ASTNode createComma();

  public abstract ASTNode createDot();

  public abstract PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral);

  /**
   * @deprecated  use the overload with language level specified
   * @param text the text to create an expression from
   * @return the expression
   */
  public abstract  PyExpression createExpressionFromText(String text);

  public abstract PyExpression createExpressionFromText(final LanguageLevel languageLevel, String text);

  public abstract PsiElement insertItemIntoList(PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
    throws IncorrectOperationException;

  @NotNull
  public abstract PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName);

  public abstract PyImportStatement createImportStatementFromText(final LanguageLevel languageLevel, String text);

  public abstract PyImportElement createImportElement(final LanguageLevel languageLevel, String name);

  @NotNull
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  @NotNull
  public abstract <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  /**
   * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
   * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
   * @param langLevel the language level to use for parsing the text
   * @param aClass    class of the PSI element; may be an interface not descending from PsiElement, as long as target node can be cast to it
   * @param text      text to parse
   * @param path      a sequence of numbers, each telling which child to select at current tree level; 0 means first child, etc.
   * @return the newly created PSI element
   */
  @NotNull
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path);

  public abstract PyNamedParameter createParameter(@NotNull String name, @Nullable String defaultValue, @Nullable String annotation,
                                                   @NotNull LanguageLevel level);

  public abstract PyNamedParameter createParameter(@NotNull String name);

  public abstract PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value);

  public abstract PsiFile createDummyFile(LanguageLevel langLevel, String contents);

  public abstract PyExpressionStatement createDocstring(String content);
  public abstract PyPassStatement createPassStatement();

  @NotNull
  public abstract PyDecoratorList createDecoratorList(@NotNull final String... decoratorTexts);
}

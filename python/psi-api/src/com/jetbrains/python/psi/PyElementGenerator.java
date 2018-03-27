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
   *
   * @param destination where the literal is destined to; used to determine the encoding.
   * @param unescaped   the string
   * @param preferUTF8 try to use UTF8 (would use ascii if false)
   * @return a newly created literal
   */
  public abstract PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination, String unescaped,
                                                                          boolean preferUTF8);

  public abstract PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped);

  public abstract PyStringLiteralExpression createStringLiteral(@NotNull StringLiteralExpression oldElement, @NotNull String unescaped);

  public abstract PyListLiteralExpression createListLiteral();

  public abstract ASTNode createComma();

  public abstract ASTNode createDot();

  public abstract PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral);

  /**
   * @param text the text to create an expression from
   * @return the expression
   * @deprecated use the overload with language level specified
   */
  public abstract PyExpression createExpressionFromText(String text);

  public abstract PyExpression createExpressionFromText(final LanguageLevel languageLevel, String text);

  /**
   * Adds elements to list inserting required commas.
   * Method is like {@link #insertItemIntoList(PyElement, PyExpression, PyExpression)} but does not add unneeded commas.
   *
   * @param list      where to add
   * @param afterThis after which element it should be added (null for add to the head)
   * @param toInsert  what to insert
   * @return newly inserted element
   */
  @NotNull
  public abstract PsiElement insertItemIntoListRemoveRedundantCommas(
    @NotNull PyElement list,
    @Nullable PyExpression afterThis,
    @NotNull PyExpression toInsert);

  public abstract PsiElement insertItemIntoList(PyElement list, @Nullable PyExpression afterThis, PyExpression toInsert)
    throws IncorrectOperationException;

  @NotNull
  public abstract PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName);

  public abstract PyImportElement createImportElement(@NotNull LanguageLevel languageLevel, @NotNull String name, @Nullable String alias);

  public abstract PyFunction createProperty(final LanguageLevel languageLevel,
                                            String propertyName,
                                            String fieldName,
                                            AccessDirection accessDirection);

  @NotNull
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  @NotNull
  public abstract <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  /**
   * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
   * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
   *
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

  /**
   * @param text parameters list already in parentheses, e.g. {@code (foo, *args, **kwargs)}.
   */
  @NotNull
  public abstract PyParameterList createParameterList(@NotNull LanguageLevel languageLevel, @NotNull String text);

  /**
   * @param text argument list already in parentheses, e.g. {@code (1, 2, *xs)}.
   */
  @NotNull
  public abstract PyArgumentList createArgumentList(@NotNull LanguageLevel languageLevel, @NotNull String text);

  public abstract PyKeywordArgument createKeywordArgument(LanguageLevel languageLevel, String keyword, String value);

  public abstract PsiFile createDummyFile(LanguageLevel langLevel, String contents);

  public abstract PyExpressionStatement createDocstring(String content);

  public abstract PyPassStatement createPassStatement();

  @NotNull
  public abstract PyDecoratorList createDecoratorList(@NotNull final String... decoratorTexts);

  /**
   * Creates new line whitespace
   */
  @NotNull
  public abstract PsiElement createNewLine();

  /**
   * Creates import statement of form {@code from qualifier import name as alias}.
   *
   * @param languageLevel language level for created element
   * @param qualifier     from where {@code name} will be imported (module name)
   * @param name          text of the reference in import element
   * @param alias         optional alias for {@code as alias} part
   * @return created {@link com.jetbrains.python.psi.PyFromImportStatement}
   */
  @NotNull
  public abstract PyFromImportStatement createFromImportStatement(@NotNull LanguageLevel languageLevel,
                                                                  @NotNull String qualifier,
                                                                  @NotNull String name,
                                                                  @Nullable String alias);

  /**
   * Creates import statement of form {@code import name as alias}.
   *
   * @param languageLevel language level for created element
   * @param name          text of the reference in import element (module name)
   * @param alias         optional alias for {@code as alias} part
   * @return created {@link com.jetbrains.python.psi.PyImportStatement}
   */
  @NotNull
  public abstract PyImportStatement createImportStatement(@NotNull LanguageLevel languageLevel,
                                                          @NotNull String name,
                                                          @Nullable String alias);
}

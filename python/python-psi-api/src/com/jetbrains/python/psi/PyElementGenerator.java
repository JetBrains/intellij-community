// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PyElementGenerator extends PyAstElementGenerator {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static PyElementGenerator getInstance(Project project) {
    return (PyElementGenerator)PyAstElementGenerator.getInstance(project);
  }

  public PyElementGenerator(Project project) {
    super(project);
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
   * @param preferDoubleQuotes try to use double/single quotes
   * @return a newly created literal
   */
  protected abstract PyStringLiteralExpression createStringLiteralFromString(@Nullable PsiFile destination,
                                                                          @NotNull String unescaped,
                                                                          boolean preferUTF8, boolean preferDoubleQuotes);

  public abstract PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped, boolean preferDoubleQuotes);

  public abstract PyStringLiteralExpression createStringLiteralFromString(@NotNull String unescaped);

  public abstract PyStringLiteralExpression createStringLiteral(@NotNull StringLiteralExpression oldElement, @NotNull String unescaped);

  public abstract PyListLiteralExpression createListLiteral();

  public abstract ASTNode createDot();

  public abstract PyBinaryExpression createBinaryExpression(String s, PyExpression expr, PyExpression listLiteral);

  @NotNull
  public abstract PyExpression createExpressionFromText(@NotNull LanguageLevel languageLevel, @NotNull String text) throws IncorrectOperationException;

  @NotNull
  public abstract PyPattern createPatternFromText(@NotNull LanguageLevel languageLevel, @NotNull String text) throws IncorrectOperationException;

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

  @Override
  public PyExpressionStatement createDocstring(String content) {
    return (PyExpressionStatement)super.createDocstring(content);
  }

  public abstract PyPassStatement createPassStatement();

  @NotNull
  public abstract PyDecoratorList createDecoratorList(final String @NotNull ... decoratorTexts);

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
   * @return created {@link PyFromImportStatement}
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
   * @return created {@link PyImportStatement}
   */
  @NotNull
  public abstract PyImportStatement createImportStatement(@NotNull LanguageLevel languageLevel,
                                                          @NotNull String name,
                                                          @Nullable String alias);

  @NotNull
  public abstract PyNoneLiteralExpression createEllipsis();

  @NotNull
  public abstract PySingleStarParameter createSingleStarParameter();
}

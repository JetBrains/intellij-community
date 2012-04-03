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

  public abstract ASTNode createNameIdentifier(String name);

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

  @Nullable
  public abstract PyCallExpression createCallExpression(final LanguageLevel langLevel, String functionName);

  public abstract PyImportStatement createImportStatementFromText(String text);

  public abstract PyImportElement createImportElement(String name);

  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text);

  /**
   * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
   * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
   * @param langLevel the language level to use for parsing the text
   * @param aClass    class of the PSI element; may be an interface not descending from PsiElement, as long as target node can be cast to it
   * @param text      text to parse
   * @param path      a sequence of numbers, each telling which child to select at current tree level; 0 means first child, etc.
   * @return the newly created PSI element
   */
  @Nullable
  public abstract <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, final String text, final int[] path);

  public abstract PyNamedParameter createParameter(@NotNull String name);

  public abstract PsiFile createDummyFile(LanguageLevel langLevel, String contents);

  public abstract PyExpressionStatement createDocstring(String content);
}

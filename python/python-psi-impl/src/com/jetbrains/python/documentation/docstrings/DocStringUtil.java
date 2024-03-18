// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DocStringUtil {
  private DocStringUtil() {
  }

  @Nullable
  public static String getDocStringValue(@NotNull PyDocStringOwner owner) {
    return DocStringUtilCore.getDocStringValue(owner);
  }

  /**
   * Attempts to detect docstring format from given text and parses it into corresponding structured docstring.
   * It's recommended to use more reliable {@link #parse(String, PsiElement)} that fallbacks to format specified in settings.
   *
   * @param text docstring text <em>with both quotes and string prefix stripped</em>
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see #parse(String, PsiElement)
   */
  @NotNull
  public static StructuredDocString parse(@NotNull String text) {
    return parse(text, null);
  }

  /**
   * Attempts to detects docstring format first from given text, next from settings and parses text into corresponding structured docstring.
   *
   * @param text   docstring text <em>with both quotes and string prefix stripped</em>
   * @param anchor PSI element that will be used to retrieve docstring format from the containing file or the project module
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   * @see DocStringFormat#ALL_NAMES_BUT_PLAIN
   * @see DocStringParser#guessDocStringFormat(String, PsiElement)
   */
  @NotNull
  public static StructuredDocString parse(@NotNull String text, @Nullable PsiElement anchor) {
    final DocStringFormat format = DocStringParser.guessDocStringFormat(text, anchor);
    return parseDocStringContent(format, text);
  }

  /**
   * Attempts to detects docstring format first from the text of given string node, next from settings using given expression as an anchor
   * and parses text into corresponding structured docstring.
   *
   * @param stringLiteral supposedly result of {@link PyDocStringOwner#getDocStringExpression()}
   * @return structured docstring for one of supported formats or instance of {@link PlainDocString} if none was recognized.
   */
  @NotNull
  public static StructuredDocString parseDocString(@NotNull PyStringLiteralExpression stringLiteral) {
    return parseDocString(DocStringParser.guessDocStringFormat(stringLiteral.getStringValue(), stringLiteral), stringLiteral);
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull PyStringLiteralExpression stringLiteral) {
    return parseDocString(format, stringLiteral.getStringNodes().get(0));
  }

  @NotNull
  public static StructuredDocString parseDocString(@NotNull DocStringFormat format, @NotNull ASTNode node) {
    //Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return DocStringParser.parseDocString(format, node.getText());
  }

  /**
   * @param stringContent docstring text without string prefix and quotes, but not escaped, otherwise ranges of {@link Substring} returned
   *                      from {@link StructuredDocString} may be invalid
   */
  @NotNull
  public static StructuredDocString parseDocStringContent(@NotNull DocStringFormat format, @NotNull String stringContent) {
    return DocStringParser.parseDocString(format, new Substring(stringContent));
  }

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyStringLiteralExpression findDocStringExpression(@Nullable PyElement parent) {
    return (PyStringLiteralExpression)DocStringUtilCore.findDocStringExpression(parent);
  }

  @Nullable
  public static StructuredDocString getStructuredDocString(@NotNull PyDocStringOwner owner) {
    final String value = owner.getDocStringValue();
    return value == null ? null : parse(value, owner);
  }

  /**
   * Returns containing docstring expression of class definition, function definition or module.
   * Useful to test whether particular PSI element is or belongs to such docstring.
   */
  @Nullable
  public static PyStringLiteralExpression getParentDefinitionDocString(@NotNull PsiElement element) {
    return (PyStringLiteralExpression)DocStringUtilCore.getParentDefinitionDocString(element);
  }

  public static boolean isDocStringExpression(@NotNull PyExpression expression) {
    if (getParentDefinitionDocString(expression) == expression) {
      return true;
    }
    if (expression instanceof PyStringLiteralExpression) {
      return isVariableDocString((PyStringLiteralExpression)expression);
    }
    return false;
  }

  @Nullable
  public static String getAttributeDocComment(@NotNull PyTargetExpression attr) {
    if (attr.getParent() instanceof PyAssignmentStatement assignment) {
      final PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSibling(assignment);
      if (prevSibling instanceof PsiComment && prevSibling.getText().startsWith("#:")) {
        return prevSibling.getText().substring(2);
      }
    }
    return null;
  }

  public static boolean isVariableDocString(@NotNull PyStringLiteralExpression expr) {
    final PsiElement parent = expr.getParent();
    if (!(parent instanceof PyExpressionStatement)) {
      return false;
    }
    final PsiElement prevElement = PyPsiUtils.getPrevNonCommentSibling(parent, true);
    if (prevElement instanceof PyAssignmentStatement assignmentStatement) {
      if (expr.getText().contains("type:")) return true;

      final ScopeOwner scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner.class);
      if (scope instanceof PyClass || scope instanceof PyFile) {
        return true;
      }
      if (scope instanceof PyFunction) {
        for (PyExpression target : assignmentStatement.getTargets()) {
          if (PyUtil.isInstanceAttribute(target)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}

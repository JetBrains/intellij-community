package com.jetbrains.python.ast.docstring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.PyAstDocStringOwner;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class DocStringUtilCore {
  private DocStringUtilCore() {
  }

  public static @Nullable String getDocStringValue(@NotNull PyAstDocStringOwner owner) {
    return PyPsiUtilsCore.strValue(owner.getDocStringExpression());
  }

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  public static @Nullable PyAstStringLiteralExpression findDocStringExpression(@Nullable PyAstElement parent) {
    if (parent != null) {
      PsiElement seeker = PyPsiUtilsCore.getNextNonCommentSibling(parent.getFirstChild(), false);
      if (seeker instanceof PyAstExpressionStatement) seeker = PyPsiUtilsCore.getNextNonCommentSibling(seeker.getFirstChild(), false);
      if (seeker instanceof PyAstStringLiteralExpression) return (PyAstStringLiteralExpression)seeker;
    }
    return null;
  }

  /**
   * Returns containing docstring expression of class definition, function definition or module.
   * Useful to test whether particular PSI element is or belongs to such docstring.
   */
  public static @Nullable PyAstStringLiteralExpression getParentDefinitionDocString(@NotNull PsiElement element) {
    final PyAstDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyAstDocStringOwner.class);
    if (docStringOwner != null) {
      final PyAstStringLiteralExpression docString = docStringOwner.getDocStringExpression();
      if (PsiTreeUtil.isAncestor(docString, element, false)) {
        return docString;
      }
    }
    return null;
  }
}

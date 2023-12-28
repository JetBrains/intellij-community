package com.jetbrains.python.ast.docstring;

import com.intellij.psi.PsiElement;
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

  @Nullable
  public static String getDocStringValue(@NotNull PyAstDocStringOwner owner) {
    return PyPsiUtilsCore.strValue(owner.getDocStringExpression());
  }

  /**
   * Looks for a doc string under given parent.
   *
   * @param parent where to look. For classes and functions, this would be PyStatementList, for modules, PyFile.
   * @return the defining expression, or null.
   */
  @Nullable
  public static PyAstStringLiteralExpression findDocStringExpression(@Nullable PyAstElement parent) {
    if (parent != null) {
      PsiElement seeker = PyPsiUtilsCore.getNextNonCommentSibling(parent.getFirstChild(), false);
      if (seeker instanceof PyAstExpressionStatement) seeker = PyPsiUtilsCore.getNextNonCommentSibling(seeker.getFirstChild(), false);
      if (seeker instanceof PyAstStringLiteralExpression) return (PyAstStringLiteralExpression)seeker;
    }
    return null;
  }
}

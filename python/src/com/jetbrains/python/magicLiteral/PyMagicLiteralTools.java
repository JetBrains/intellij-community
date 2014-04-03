package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tools that help user to work with magic literals.
 *
 * @author Ilya.Kazakevich
 */
public final class PyMagicLiteralTools {
  private PyMagicLiteralTools() {
  }


  /**
   * Checks if literal is magic (there is some extension point that supports it)
   *
   * @param element element to check
   * @return true if magic
   */
  public static boolean isMagicLiteral(@NotNull final PsiElement element) {
    return (element instanceof PyStringLiteralExpression) && (getPoint((PyStringLiteralExpression)element) != null);
  }

  /**
   * Gets extension point by literal.
   *
   * @param element literal
   * @return extension point (if any) or null if literal is unknown to all installed magic literal extesnion points
   */
  @Nullable
  public static PyMagicLiteralExtensionPoint getPoint(@NotNull final PyStringLiteralExpression element) {
    final PyMagicLiteralExtensionPoint[] magicLiteralExtPoints =
      ApplicationManager.getApplication().getExtensions(PyMagicLiteralExtensionPoint.EP_NAME);

    for (final PyMagicLiteralExtensionPoint magicLiteralExtensionPoint : magicLiteralExtPoints) {
      if (magicLiteralExtensionPoint.isMagicLiteral(element)) {
        return magicLiteralExtensionPoint;
      }
    }
    return null;
  }
}

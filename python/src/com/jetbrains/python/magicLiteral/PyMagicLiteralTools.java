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
package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiCacheKey;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tools that help user to work with magic literals.
 *
 * @author Ilya.Kazakevich
 */
public final class PyMagicLiteralTools {
  /**
   * Cache: ref (value may be null or extension point) by by string literal
   */
  private final static PsiCacheKey<Ref<PyMagicLiteralExtensionPoint>, StringLiteralExpression> MAGIC_LITERAL_POINT =
    PsiCacheKey
      .create(PyMagicLiteralTools.class.getName(), new MagicLiteralChecker(), PsiModificationTracker.MODIFICATION_COUNT);

  private PyMagicLiteralTools() {
  }


  /**
   * Checks if literal is magic (there is some extension point that supports it)
   *
   * @param element element to check
   * @return true if magic
   */
  public static boolean isMagicLiteral(@NotNull final PsiElement element) {
    return (element instanceof StringLiteralExpression) && (getPoint((StringLiteralExpression)element) != null);
  }

  /**
   * Gets extension point by literal.
   *
   * @param element literal
   * @return extension point (if any) or null if literal is unknown to all installed magic literal extesnion points
   */
  @Nullable
  public static PyMagicLiteralExtensionPoint getPoint(@NotNull final StringLiteralExpression element) {
    return MAGIC_LITERAL_POINT.getValue(element).get();
  }

  /**
   * Obtains ref (value may be null or extension point) by by string literal
   */
  private static class MagicLiteralChecker implements Function<StringLiteralExpression, Ref<PyMagicLiteralExtensionPoint>> {
    @Override
    public Ref<PyMagicLiteralExtensionPoint> fun(StringLiteralExpression element) {
      final PyMagicLiteralExtensionPoint[] magicLiteralExtPoints =
        ApplicationManager.getApplication().getExtensions(PyMagicLiteralExtensionPoint.EP_NAME);

      for (final PyMagicLiteralExtensionPoint magicLiteralExtensionPoint : magicLiteralExtPoints) {
        if (magicLiteralExtensionPoint.isMagicLiteral(element)) {
          return Ref.create(magicLiteralExtensionPoint);
        }
      }
      return new Ref<>();
    }
  }
}

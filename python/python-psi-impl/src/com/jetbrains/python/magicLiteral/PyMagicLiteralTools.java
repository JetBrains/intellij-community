// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.magicLiteral;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

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
  public static boolean couldBeMagicLiteral(@NotNull final PsiElement element) {
    return (element instanceof StringLiteralExpression) && (element.getReferences().length == 0) &&
           Arrays.stream(PyMagicLiteralExtensionPoint.EP_NAME.getExtensions()).anyMatch(o-> o.isEnabled(element));
  }

  /**
   * Gets extension point by literal.
   *
   * @param element literal
   * @return extension point (if any) or null if literal is unknown to all installed magic literal extension points
   */
  @Nullable
  public static PyMagicLiteralExtensionPoint getPoint(@NotNull StringLiteralExpression element) {
    return CachedValuesManager.getCachedValue(element, () -> {
      return Result.create(PyMagicLiteralExtensionPoint.EP_NAME.findFirstSafe(ep -> ep.isMagicLiteral(element)),
                           PsiModificationTracker.MODIFICATION_COUNT);
    });
  }
}

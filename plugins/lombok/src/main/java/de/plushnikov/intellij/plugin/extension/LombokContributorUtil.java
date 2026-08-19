// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LombokContributorUtil {
  private LombokContributorUtil() {
  }

  /// Checks whether lombok contributes getter that is generated based on `psiField`.
  ///
  /// @param psiField field to check
  /// @return true if lombok contributes a getter that is generated based on `psiField`.
  public static boolean isGetterContributedFor(@NotNull PsiField psiField) {
    for (Processor processor : LombokProcessorManager.getProcessors(PsiMethod.class)) {
      if (processor.contributesGetter(psiField)) {
        return true;
      }
    }
    return false;
  }

  /// Checks whether lombok contributes a setter that is generated based on `psiField`.
  ///
  /// @param psiField field to check
  /// @return true if lombok contributes a setter that is generated based on `psiField`.
  public static boolean isSetterContributedFor(@NotNull PsiField psiField) {
    for (Processor processor : LombokProcessorManager.getProcessors(PsiMethod.class)) {
      if (processor.contributesSetter(psiField)) {
        return true;
      }
    }
    return false;
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReference;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public interface PyUnresolvedReferenceQuickFixProvider {
  ExtensionPointName<PyUnresolvedReferenceQuickFixProvider> EP_NAME = ExtensionPointName.create("Pythonid.unresolvedReferenceQuickFixProvider");

  /**
   * @deprecated Override the more generic {@link #registerQuickFixes(PsiReference, List)}.
   */
  @Deprecated(forRemoval = true)
  default void registerQuickFixes(PsiReference reference, Consumer<LocalQuickFix> fixConsumer) {
    throw new UnsupportedOperationException();
  }

  /**
   * @param reference The reference containing an unresolved import.
   * @param existing All already suggested quick fixes, including not only import fixes.
   */
  default void registerQuickFixes(@NotNull PsiReference reference, @NotNull List<LocalQuickFix> existing) {
    registerQuickFixes(reference, existing::add);
  }
}

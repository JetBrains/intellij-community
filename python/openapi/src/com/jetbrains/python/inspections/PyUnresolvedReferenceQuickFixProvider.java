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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReference;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public interface PyUnresolvedReferenceQuickFixProvider {
  ExtensionPointName<PyUnresolvedReferenceQuickFixProvider> EP_NAME = ExtensionPointName.create("Pythonid.unresolvedReferenceQuickFixProvider");

  /**
   * @deprecated Override the more generic {@link #registerQuickFixes(PsiReference, List)}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
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

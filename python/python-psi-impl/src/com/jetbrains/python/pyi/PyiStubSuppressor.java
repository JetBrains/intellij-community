// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.pyi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PyiStubSuppressor {
  ExtensionPointName<PyiStubSuppressor> EP_NAME = ExtensionPointName.create("Pythonid.pyiStubSuppressor");

  /**
   * @param file pyi file containing stubs for the corresponding module.
   * @return true if the specified {@code file} should not be used in code insight, false otherwise.
   */
  boolean isIgnoredStub(@NotNull PyiFile file);

  static boolean isIgnoredStub(@Nullable PsiFile file) {
    if (!(file instanceof PyiFile)) return false;
    return EP_NAME.getExtensionList().stream().anyMatch(it -> it.isIgnoredStub((PyiFile)file));
  }
}

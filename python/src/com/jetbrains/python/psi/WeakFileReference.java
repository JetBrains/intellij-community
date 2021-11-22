// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;


public class WeakFileReference extends FileReferenceWithOneContext implements PsiReferenceEx {
  public WeakFileReference(FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  @Nullable
  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    return HighlightSeverity.WARNING;
  }

  @Nullable
  @Override
  public @Nls String getUnresolvedDescription() {
    return null;
  }
}

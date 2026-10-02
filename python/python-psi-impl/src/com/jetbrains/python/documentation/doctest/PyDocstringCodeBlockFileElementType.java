// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.Language;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyDocstringCodeBlockFileElementType extends PyFileElementType {
  public PyDocstringCodeBlockFileElementType(Language language) {
    super(language);
  }

  @Override
  public @NotNull String getExternalId() {
    return "PyDocstringCodeBlock.FILE";
  }
}

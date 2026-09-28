// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.doctest;

import com.intellij.lang.Language;
import com.jetbrains.python.psi.PyFileElementType;
import org.jetbrains.annotations.NotNull;

public class PyDocstringCodeBlockFileElementType extends PyFileElementType {
  public PyDocstringCodeBlockFileElementType(Language language) {
    super(language);
  }

  @Override
  public @NotNull String getExternalId() {
    return "PyDocstringCodeBlock.FILE";
  }
}

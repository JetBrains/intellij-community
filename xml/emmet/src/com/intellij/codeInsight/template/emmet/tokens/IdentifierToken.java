// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.tokens;

import org.jetbrains.annotations.NotNull;

public class IdentifierToken extends ZenCodingToken {
  private final String myText;

  public IdentifierToken(@NotNull String text) {
    myText = text;
  }

  @Override
  public String toString() {
    return myText;
  }

  public String getText() {
    return myText;
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.tokens;

public class ZenCodingTokenImpl extends ZenCodingToken {
  private final String myString;

  public ZenCodingTokenImpl(String string) {
    myString = string;
  }

  @Override
  public String toString() {
    return myString;
  }
}

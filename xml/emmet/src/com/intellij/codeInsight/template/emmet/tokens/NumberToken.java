// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.tokens;

public class NumberToken extends ZenCodingToken {
  private final int myNumber;

  public NumberToken(int number) {
    myNumber = number;
  }

  public int getNumber() {
    return myNumber;
  }

  @Override
  public String toString() {
    return Integer.toString(myNumber);
  }
}

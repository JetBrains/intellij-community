// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.tokens;

public class OperationToken extends ZenCodingToken {
  private final char mySign;

  public OperationToken(char sign) {
    mySign = sign;
  }

  public char getSign() {
    return mySign;
  }

  @Override
  public String toString() {
    return Character.toString(mySign);
  }
}

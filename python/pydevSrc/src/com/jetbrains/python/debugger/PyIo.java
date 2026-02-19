// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

public class PyIo {
  private final String text;
  private final int ctx;

  public PyIo(String text, int ctx) {
    this.text = text;
    this.ctx = ctx;
  }

  public String getText() {
    return text;
  }

  public int getCtx() {
    return ctx;
  }
}

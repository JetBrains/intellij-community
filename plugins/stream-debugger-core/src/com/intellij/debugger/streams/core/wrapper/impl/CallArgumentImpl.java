// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.wrapper.impl;

import com.intellij.debugger.streams.core.wrapper.CallArgument;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class CallArgumentImpl implements CallArgument {
  private final @NotNull String myType;
  private final @NotNull String myText;

  public CallArgumentImpl(@NotNull String type, @NotNull String text) {
    myType = type;
    myText = text;
  }

  @Override
  public @NotNull @NlsSafe String getType() {
    return myType;
  }

  @Override
  public @NotNull @NlsSafe String getText() {
    return myText;
  }
}

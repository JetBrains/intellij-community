// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper.impl;

import com.intellij.debugger.streams.wrapper.CallArgument;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class CallArgumentImpl implements CallArgument {
  @NotNull private final String myType;
  @NotNull private final String myText;

  public CallArgumentImpl(@NotNull String type, @NotNull String text) {
    myType = type;
    myText = text;
  }

  @NotNull
  @Override
  public String getType() {
    return myType;
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }
}

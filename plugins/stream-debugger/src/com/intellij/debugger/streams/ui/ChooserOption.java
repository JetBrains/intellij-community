// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public interface ChooserOption {
  @NotNull
  Stream<TextRange> rangeStream();

  @NotNull
  String getText();
}

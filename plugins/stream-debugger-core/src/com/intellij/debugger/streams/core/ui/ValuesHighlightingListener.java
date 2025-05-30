// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.ui;

import com.intellij.debugger.streams.core.trace.TraceElement;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface ValuesHighlightingListener extends EventListener {
  void highlightingChanged(@NotNull List<TraceElement> values, @NotNull PropagationDirection direction);
}

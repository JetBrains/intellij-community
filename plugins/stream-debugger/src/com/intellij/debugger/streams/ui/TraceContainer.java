// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface TraceContainer extends Disposable {
  void highlight(@NotNull List<TraceElement> elements);

  void select(@NotNull List<TraceElement> elements);

  void addSelectionListener(@NotNull ValuesSelectionListener listener);

  boolean highlightedExists();
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface MethodCall {
  @NotNull
  @NlsSafe String getName();

  @NotNull
  @NlsSafe String getGenericArguments();

  @NotNull
  List<CallArgument> getArguments();

  @NotNull
  TextRange getTextRange();

  @NotNull
  default @NlsSafe String getTabTitle() {
    return getName().replace(" ", "") + getGenericArguments();
  }

  @NotNull
  default @NlsSafe String getTabTooltip() {
    return TraceUtil.formatWithArguments(this);
  }
}

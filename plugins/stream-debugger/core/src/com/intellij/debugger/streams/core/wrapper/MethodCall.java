// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.wrapper;

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

  /**
   * @return Returns a string, representing all generic arguments of a function in a chain surrounded by brackets. In C# there are calls, that look like
   * collection.Cast&lt;int&gt;()`, so for this call this method should return `&lt;int&gt;`.  This string is necessary for a code generator to recreate
   * a method call for evaluation.
   */
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

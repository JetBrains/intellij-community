// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface CompletionContext {
  @NotNull
  CompletionType getCompletionType();

  int getInvocationCount();

  @NotNull
  String getCompletionPrefix();

  @NotNull
  CompletionResultSet getCompletionResultSet();
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.jetbrains.python.codeInsight.completion.PythonLookupElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Adjusts indentation after a final part keyword is inserted, e.g. an "else:".
 */
@ApiStatus.Internal
public final class PyUnindentingInsertHandler implements InsertHandler<PythonLookupElement> {
  public static final PyUnindentingInsertHandler INSTANCE = new PyUnindentingInsertHandler();

  private PyUnindentingInsertHandler() {
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull PythonLookupElement item) {
    PyUnindentUtil.unindentAsNeeded(context.getProject(), context.getEditor(), context.getFile());
  }
}

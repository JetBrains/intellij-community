// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;

import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class HtmlSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @Override
  protected @NotNull SyntaxHighlighter createHighlighter() {
    return new HtmlFileHighlighter();
  }
}

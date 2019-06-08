// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.highlighter;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class ShHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @Override
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new ShSyntaxHighlighter();
  }
}

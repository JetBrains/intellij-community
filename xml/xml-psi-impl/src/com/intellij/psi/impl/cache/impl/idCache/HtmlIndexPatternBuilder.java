// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.HtmlLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlIndexPatternBuilder extends XmlIndexPatternBuilder {
  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (HtmlUtil.isHtmlFile(file)) {
      return new HtmlLexer(true);
    }
    return null;
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlIndexPatternBuilder extends XmlIndexPatternBuilder {
  @Nullable
  @Override
  public Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (HtmlUtil.isHtmlFile(file)) {
      return new HtmlHighlightingLexer(FileTypeManager.getInstance().getStdFileType("CSS"));
    }
    return null;
  }
}

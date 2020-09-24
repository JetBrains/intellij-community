// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class TemplateHtmlScriptContentProvider implements HtmlScriptContentProvider {
  @Override
  public IElementType getScriptElementType() {
    return XmlElementType.HTML_EMBEDDED_CONTENT;
  }

  @Nullable
  @Override
  public Lexer getHighlightingLexer() {
    return new HtmlHighlightingLexer(HtmlFileType.INSTANCE);
  }
}

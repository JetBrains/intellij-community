// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lexer.HtmlLexer;
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

  @Override
  public @Nullable Lexer getHighlightingLexer() {
    return new HtmlLexer(true);
  }
}

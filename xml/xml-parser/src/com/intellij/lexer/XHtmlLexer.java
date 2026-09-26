/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lexer;

import com.intellij.html.embedding.HtmlEmbeddedContentProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class XHtmlLexer extends HtmlLexer {
  public XHtmlLexer(Lexer baseLexer) {
    super(baseLexer, false);
  }

  public XHtmlLexer(Lexer baseLexer, boolean highlightMode) {
    super(baseLexer, false, highlightMode);
  }

  public XHtmlLexer() {
    this(XmlLexerKt.createXmlLexer(true));
  }

  public XHtmlLexer(boolean highlightMode) {
    this(XmlLexerKt.createXmlLexer(true), highlightMode);
  }

  @Override
  protected boolean isHtmlTagState(int state) {
    return state == __XmlLexer.TAG || state == __XmlLexer.END_TAG;
  }

  @Override
  protected boolean acceptEmbeddedContentProvider(@NotNull HtmlEmbeddedContentProvider provider) {
    return !(provider instanceof HtmlRawTextTagContentProvider);
  }
}

package com.intellij.lexer;

public class XHtmlHighlightingLexer extends HtmlHighlightingLexer {
  public XHtmlHighlightingLexer() {
    this(new XmlLexer());
  }

  protected XHtmlHighlightingLexer(Lexer baseLexer) {
    super(baseLexer,false);
  }

  protected boolean isHtmlTagState(int state) {
    return state == _XmlLexer.TAG || state == _XmlLexer.END_TAG;
  }
}
package com.intellij.lexer;

public class XHtmlHighlightingLexer extends HtmlHighlightingLexer {
  public XHtmlHighlightingLexer() {
    this(new XmlLexer(),false);
  }

  protected XHtmlHighlightingLexer(Lexer baseLexer, boolean withEl) {
    super(baseLexer,false,withEl);
  }

  protected boolean isHtmlTagState(int state) {
    return state == 0;
  }
}
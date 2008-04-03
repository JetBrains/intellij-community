package com.intellij.lexer;

public class XHtmlHighlightingLexer extends HtmlHighlightingLexer {
  public XHtmlHighlightingLexer() {
    this(new XmlLexer());
  }

  public XHtmlHighlightingLexer(Lexer baseLexer) {
    super(baseLexer,false);
  }

  protected boolean isHtmlTagState(int state) {
    return state == __XmlLexer.TAG || state == __XmlLexer.END_TAG;
  }
}
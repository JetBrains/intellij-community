package com.intellij.lexer;

public class XHtmlHighlightingLexer extends HtmlHighlightingLexer {
  public XHtmlHighlightingLexer() {
    super(new XmlLexer(),false);
  }

  protected boolean isHtmlTagState(int state) {
    return state == _XmlLexer.TAG || state == _XmlLexer.END_TAG;
  }
}
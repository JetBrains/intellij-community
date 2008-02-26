package com.intellij.lexer;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 7, 2004
 * Time: 5:17:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class XHtmlLexer extends HtmlLexer {
  public XHtmlLexer(Lexer baseLexer) {
    super(baseLexer,false);
  }

  public XHtmlLexer() {
    this(new XmlLexer());
  }

  protected boolean isHtmlTagState(int state) {
    return state == __XmlLexer.TAG;
  }
}

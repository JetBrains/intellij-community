package com.intellij.lexer;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 7, 2004
 * Time: 5:17:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class XHtmlLexer extends HtmlLexer {
  public XHtmlLexer() {
    super(new XmlLexer(),false);
  }

  protected boolean isHtmlTagState(int state) {
    return state == _XmlLexer.TAG;
  }
}

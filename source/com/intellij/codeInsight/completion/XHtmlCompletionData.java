package com.intellij.codeInsight.completion;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 28.11.2004
 * Time: 0:23:35
 * To change this template use File | Settings | File Templates.
 */
public class XHtmlCompletionData extends HtmlCompletionData {
  public XHtmlCompletionData() {
    super(false);
  }

  protected boolean isCaseInsensitive() {
    return false;
  }
}

package com.intellij.lang.xhtml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:01:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLLanguage extends Language {
  public XHTMLLanguage() {
    super("XHTML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter(false,true);
  }
}

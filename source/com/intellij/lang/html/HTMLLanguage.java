package com.intellij.lang.html;

import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:00:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class HTMLLanguage extends Language {
  public HTMLLanguage() {
    super("HTML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new HtmlFileHighlighter();
  }
}

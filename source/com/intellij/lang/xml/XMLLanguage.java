package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends Language {
  public XMLLanguage() {
    super("XML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter();
  }
}

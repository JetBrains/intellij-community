package com.intellij.lang.dtd;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:53:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class DTDLanguage extends Language {
  public DTDLanguage() {
    super("DTD");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    //TODO: should be antoher?
    return new XmlFileHighlighter(true);
  }

  public ParserDefinition getParserDefinition() {
    return new DTDParserDefinition();
  }
}

package com.intellij.lang.xhtml;

import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlLexer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:02:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLParserDefinition extends XMLParserDefinition {

  @NotNull
  public Lexer createLexer(Project project) {
    return new XHtmlLexer();
  }
}

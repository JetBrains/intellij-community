package com.intellij.lang.xhtml;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:01:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLLanguage extends XMLLanguage {
  public XHTMLLanguage() {
    super("XHTML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter(false,true);
  }

  public PseudoTextBuilder getFormatter() {
    return null;
  }

  public XmlPsiPolicy getPsiPolicy() {
    return ENCODE_EACH_SYMBOL_POLICY;
  }

  public ParserDefinition getParserDefinition(Project project) {
    return new XHTMLParserDefinition(project);
  }
}

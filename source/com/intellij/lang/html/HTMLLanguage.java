package com.intellij.lang.html;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.xml.html.HtmlPseudoTextBuilder;
import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.tree.ElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:00:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class HTMLLanguage extends XMLLanguage {
  public HTMLLanguage() {
    super("HTML");
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new HtmlFileHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    return new HtmlPseudoTextBuilder();
  }

  public XmlPsiPolicy getPsiPolicy() {
    return ENCODE_EACH_SYMBOL_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new HTMLParserDefinition();
  }
}

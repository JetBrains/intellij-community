package com.intellij.lang.html;

import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:00:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class HTMLLanguage extends XMLLanguage {
  public HTMLLanguage() {
    super("HTML", "text/html","text/htmlh");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new HtmlFileHighlighter();
      }
    });
    
  }

  public XmlPsiPolicy getPsiPolicy() {
    return ENCODE_EACH_SYMBOL_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new HTMLParserDefinition();
  }
}

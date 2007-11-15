package com.intellij.lang.xhtml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
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
 * Time: 11:01:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class XHTMLLanguage extends XMLLanguage {
  public XHTMLLanguage() {
    super("XHTML", "text/xhtml", "application/xhtml+xml");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new XmlFileHighlighter(false, true);
      }
    });
  }

  public XmlPsiPolicy getPsiPolicy() {
    return ENCODE_EACH_SYMBOL_POLICY;
  }
}

/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HtmlParsing;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.psi.tree.TokenSet;

public class HtmlBuilderDriver extends XmlBuilderDriver {
  public HtmlBuilderDriver(final CharSequence text) {
    super(text);
  }

  protected PsiBuilderImpl createBuilderAndParse() {
    final ParserDefinition htmlParserDef = LanguageParserDefinitions.INSTANCE.forLanguage(HTMLLanguage.INSTANCE);
    assert htmlParserDef != null;

    PsiBuilderImpl b = new PsiBuilderImpl(htmlParserDef.createLexer(null), htmlParserDef.getWhitespaceTokens(), TokenSet.EMPTY, getText());
    new HtmlParsing(b).parseDocument();
    return b;
  }
}
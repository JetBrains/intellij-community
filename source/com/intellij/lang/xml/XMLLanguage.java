package com.intellij.lang.xml;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.codeFormatting.xml.xml.XmlPseudoTextBuilder;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends Language {
  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();
  private static final FindUsagesManager.HtmlFindUsagesHandler FIND_USAGES_HANDLER = new FindUsagesManager.HtmlFindUsagesHandler();


  public XMLLanguage() {
    super("XML");
  }

  protected XMLLanguage(String str) {
    super(str);
  }

  public SyntaxHighlighter getSyntaxHighlighter(Project project) {
    return new XmlFileHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    return new XmlPseudoTextBuilder();
  }

  public XmlPsiPolicy getPsiPolicy(){
    return CDATA_ON_ANY_ENCODED_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new XMLParserDefinition();
  }

  public FindUsagesProvider getFindUsagesProvider() {
    return FIND_USAGES_HANDLER;
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }

  public boolean mayHaveReferences(IElementType token) {
    if(token == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) return true;
    if(token == XmlTokenType.XML_DATA_CHARACTERS) return true;
    return super.mayHaveReferences(token);
  }
}

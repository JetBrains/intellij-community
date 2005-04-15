package com.intellij.lang.dtd;

import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.xml.XmlCommenter;
import com.intellij.lang.xml.XmlFindUsagesProvider;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.xml.XmlTokenType;

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

  public FindUsagesProvider getFindUsagesProvider() {
    return new XmlFindUsagesProvider();
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }

  public boolean mayHaveReferences(IElementType token, short searchContext) {
    if((searchContext & UsageSearchContext.IN_PLAIN_TEXT) != 0) return true;
    return false;
  }
}

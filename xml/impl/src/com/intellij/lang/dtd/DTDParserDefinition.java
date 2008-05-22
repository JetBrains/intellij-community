package com.intellij.lang.dtd;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.OldXmlLexer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 12:43:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class DTDParserDefinition extends XMLParserDefinition {
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final OldXmlLexer xmlLexer = new OldXmlLexer();
    return LanguageUtil.canStickTokensTogetherByLexer(left, right, xmlLexer, 0);
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.DTD_FILE);
  }

}

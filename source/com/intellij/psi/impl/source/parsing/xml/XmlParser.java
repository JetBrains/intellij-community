/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

public class XmlParser implements PsiParser {

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    builder.enforeCommentTokens(TokenSet.EMPTY);
    final PsiBuilder.Marker doc = builder.mark();
    new XmlParsing(builder).parseDocument();
    doc.done(XmlElementType.XML_DOCUMENT);
    return builder.getTreeBuilt();
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyAdapter;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.IXmlTagElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class XmlWhiteSpaceFormattingStrategy extends WhiteSpaceFormattingStrategyAdapter {

  private static void addWhitespaceToTagBody(final ASTNode treePrev, final LeafElement whiteSpaceElement) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(treePrev);
    final ASTNode treeParent = treePrev.getTreeParent();

    final boolean before;
    final XmlText xmlText;
    if(treePrev.getElementType() == XmlElementType.XML_TEXT) {
      xmlText = (XmlText)treePrev.getPsi();
      before = true;
    }
    else if(treePrev.getTreePrev().getElementType() == XmlElementType.XML_TEXT){
      xmlText = (XmlText)treePrev.getTreePrev().getPsi();
      before = false;
    }
    else{
      xmlText = (XmlText)Factory.createCompositeElement(XmlElementType.XML_TEXT, charTable, treeParent.getPsi().getManager());
      CodeEditUtil.setNodeGenerated(xmlText.getNode(), true);
      treeParent.addChild(xmlText.getNode(), treePrev);
      before = true;
    }
    final ASTNode node = xmlText.getNode();
    assert node != null;
    final TreeElement anchorInText = (TreeElement) (before ? node.getFirstChildNode() : node.getLastChildNode());
    if (anchorInText == null) node.addChild(whiteSpaceElement);
    else if (anchorInText.getElementType() != XmlTokenType.XML_WHITE_SPACE) node.addChild(whiteSpaceElement, before ? anchorInText : null);
    else {
      final String text = before ? whiteSpaceElement.getText() + anchorInText.getText() : anchorInText.getText() +
                                                                                          whiteSpaceElement.getText();
      node.replaceChild(anchorInText, ASTFactory.whitespace(text));
    }
  }

  protected boolean isInsideTagBody(@NotNull ASTNode place) {
    final ASTNode treeParent = place.getTreeParent();
    if (!(treeParent.getElementType() instanceof IXmlTagElementType)) return false;
    while(place != null){
      if(place.getElementType() == XmlTokenType.XML_TAG_END) return true;
      place = place.getTreePrev();
    }
    return false;
  }

  @Override
  public boolean addWhitespace(final @NotNull ASTNode treePrev, final @NotNull LeafElement whiteSpaceElement) {
    if (isInsideTagBody(treePrev)) {
      addWhitespaceToTagBody(treePrev, whiteSpaceElement);
      return true;
    }

    return false;
  }

  @Override
  public boolean containsWhitespacesOnly(final @NotNull ASTNode node) {
    return (node.getElementType() == XmlTokenType.XML_DATA_CHARACTERS) &&
           node.getText().trim().isEmpty();
  }
}

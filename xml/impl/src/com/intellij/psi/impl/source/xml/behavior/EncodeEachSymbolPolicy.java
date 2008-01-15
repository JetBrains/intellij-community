package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;

public class EncodeEachSymbolPolicy extends DefaultXmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable charTableByTree) {
    if(!toCode(displayText)) return super.encodeXmlTextContents(displayText, text, charTableByTree);
    final FileElement dummyParent = new JavaDummyHolder(text.getManager(), null, charTableByTree).getTreeElement();
    int sectionStartOffset = 0;
    int offset = 0;
    while (offset < displayText.length()) {
      if (toCode(displayText.charAt(offset))) {
        final String plainSection = displayText.substring(sectionStartOffset, offset);
        if (plainSection.length() > 0) {
          TreeUtil.addChildren(dummyParent, (TreeElement)super.encodeXmlTextContents(plainSection, text, charTableByTree));
        }
        TreeUtil.addChildren(dummyParent, createCharEntity(displayText.charAt(offset), dummyParent.getCharTable()));
        sectionStartOffset = offset + 1;
      }
      offset++;
    }
    final String plainSection = displayText.substring(sectionStartOffset, offset);
    if (plainSection.length() > 0) {
      TreeUtil.addChildren(dummyParent, (TreeElement)super.encodeXmlTextContents(plainSection, text, charTableByTree));
    }

    dummyParent.acceptTree(new GeneratedMarkerVisitor());
    return dummyParent.getFirstChildNode();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static TreeElement createCharEntity(char ch, CharTable charTable) {
    switch (ch) {
      case '<':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&lt;", 0, 4, charTable);
      case '\'':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&apos;", 0, 6, charTable);
      case '"':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&quot;", 0, 6, charTable);
      case '>':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&gt;", 0, 4, charTable);
      case '&':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&amp;", 0, 5, charTable);
      case '\u00a0':
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, "&nbsp;", 0, 6, charTable);

      default:
        final String charEncoding = "&#" + (int)ch + ";";
        return ASTFactory.leaf(XmlTokenType.XML_CHAR_ENTITY_REF, charEncoding, 0, charEncoding.length(), charTable);
    }
  }

  private static final boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      final char ch = str.charAt(i);
      if ( toCode(ch)) return true;
    }
    return false;
  }

  private static boolean toCode(final char ch) {
    return "<&>\u00a0'\"".indexOf(ch) >= 0;
  }

}

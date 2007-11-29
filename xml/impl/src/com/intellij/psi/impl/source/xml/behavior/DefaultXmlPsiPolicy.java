package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.xml.*;
import com.intellij.util.CharTable;

public class DefaultXmlPsiPolicy implements XmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable table) {
    final XmlTag rootTag =
      ((XmlFile)text.getManager().getElementFactory().createFileFromText("a.xml", "<a>" + displayText + "</a>")).getDocument().getRootTag();
    assert rootTag != null;
    final XmlTagChild[] tagChildren = rootTag.getValue().getChildren();
    final XmlTagChild child = tagChildren.length > 0 ? tagChildren[0]:null;
    assert child != null;
    final FileElement dummyParent = new DummyHolder(text.getManager(), null, table).getTreeElement();
    final TreeElement element = (TreeElement)child.getNode();
    TreeUtil.removeRange(element.getTreeNext(), null);
    TreeUtil.addChildren(dummyParent, element);
    TreeUtil.clearCaches(dummyParent);
    return element.getFirstChildNode();
  }

  private static LeafElement createNextToken(final int startOffset,
                                      final int endOffset,
                                      final boolean isWhitespace,
                                      final FileElement dummyParent,
                                      final CharSequence chars) {
    if(startOffset != endOffset){
      if(isWhitespace){
        return Factory.createLeafElement(
          XmlTokenType.XML_WHITE_SPACE,
          chars,
          startOffset,
          endOffset, dummyParent.getCharTable());
      }
      else{
        return Factory.createLeafElement(
          XmlTokenType.XML_DATA_CHARACTERS,
          chars,
          startOffset,
          endOffset, dummyParent.getCharTable());
      }
    }
    return null;
  }

}

package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;

public class DefaultXmlPsiPolicy implements XmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, CharTable table) {
    boolean wsChars = false;
    final FileElement dummyParent = new DummyHolder(null, null, table).getTreeElement();
    int fragmentStart = 0;
    final char[] chars = displayText.toCharArray();
    for(int i = 0; i < displayText.length(); i++){
      if(wsChars != Character.isWhitespace(displayText.charAt(i))){
        final ASTNode next = createNextToken(fragmentStart, i, wsChars, dummyParent, chars);
        if(next != null){
          TreeUtil.addChildren(dummyParent, (TreeElement)next);
          fragmentStart = i;
        }
        wsChars = Character.isWhitespace(displayText.charAt(i));
      }
    }
    final ASTNode next = createNextToken(fragmentStart, displayText.length(), wsChars, dummyParent, chars);
    if(next != null) TreeUtil.addChildren(dummyParent, (TreeElement)next);
    return dummyParent.getFirstChildNode();
  }

  private LeafElement createNextToken(final int startOffset,
                                      final int endOffset,
                                      final boolean isWhitespace,
                                      final FileElement dummyParent,
                                      final char[] chars) {
    if(startOffset != endOffset){
      if(isWhitespace){
        return Factory.createLeafElement(
          XmlTokenType.XML_WHITE_SPACE,
          chars,
          startOffset,
          endOffset, -1, dummyParent.getCharTable());
      }
      else{
        return Factory.createLeafElement(
          XmlTokenType.XML_DATA_CHARACTERS,
          chars,
          startOffset,
          endOffset, -1, dummyParent.getCharTable());
      }
    }
    return null;
  }

  public static final boolean toCode(String str) {
    for (int i = 0; i < str.length(); i++) {
      if (toCode(str.charAt(i))) return true;
    }
    return false;
  }

  public static final boolean toCode(char ch) {
    return "<&>".indexOf(ch) >= 0;
  }

  public ASTNode encodeXmlTextContents(String displayText) {
    return encodeXmlTextContents(displayText, null);
  }
}

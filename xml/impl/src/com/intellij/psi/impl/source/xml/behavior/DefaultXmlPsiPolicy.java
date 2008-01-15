package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.*;
import com.intellij.util.CharTable;

public class DefaultXmlPsiPolicy implements XmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable table) {
    final PsiFile containingFile = text.getContainingFile();
    final FileElement dummyParent = new JavaDummyHolder(text.getManager(), null, table).getTreeElement();

    if (containingFile instanceof JspFile) {
      boolean wsChars = false;
      int fragmentStart = 0;

      for(int i = 0; i < displayText.length(); i++){
        if(wsChars != Character.isWhitespace(displayText.charAt(i))){
          final ASTNode next = createNextToken(fragmentStart, i, wsChars, dummyParent, displayText);
          if(next != null){
            TreeUtil.addChildren(dummyParent, (TreeElement)next);
            fragmentStart = i;
          }
          wsChars = Character.isWhitespace(displayText.charAt(i));
        }
      }
      final ASTNode next = createNextToken(fragmentStart, displayText.length(), wsChars, dummyParent, displayText);
      if(next != null) TreeUtil.addChildren(dummyParent, (TreeElement)next);
      dummyParent.acceptTree(new GeneratedMarkerVisitor());
      return dummyParent.getFirstChildNode();
    } else {
      final XmlTag rootTag =
        ((XmlFile)PsiFileFactory.getInstance(containingFile.getProject())
          .createFileFromText("a.xml", "<a>" + displayText + "</a>")).getDocument().getRootTag();

      assert rootTag != null;
      final XmlTagChild[] tagChildren = rootTag.getValue().getChildren();
      final XmlTagChild child = tagChildren.length > 0 ? tagChildren[0]:null;
      assert child != null;

      final TreeElement element = (TreeElement)child.getNode();
      TreeUtil.removeRange((TreeElement)tagChildren[tagChildren.length - 1].getNode().getTreeNext(), null);
      TreeUtil.addChildren(dummyParent, element);
      TreeUtil.clearCaches(dummyParent);
      return element.getFirstChildNode();
    }
  }

  private static LeafElement createNextToken(final int startOffset,
                                      final int endOffset,
                                      final boolean isWhitespace,
                                      final FileElement dummyParent,
                                      final CharSequence chars) {
    if(startOffset != endOffset){
      if(isWhitespace){
        return ASTFactory.leaf(XmlTokenType.XML_WHITE_SPACE, chars, startOffset, endOffset, dummyParent.getCharTable());
      }
      else{
        return ASTFactory.leaf(XmlTokenType.XML_DATA_CHARACTERS, chars, startOffset, endOffset, dummyParent.getCharTable());
      }
    }
    return null;
  }

}

/*
 * @author max
 */
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;

public class XmlFormatterUtilHelper implements FormatterUtilHelper {
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
      final LeafElement singleLeafElement = Factory.createSingleLeafElement(XmlTokenType.XML_WHITE_SPACE, text, 0,
                                                                            text.length(), charTable, xmlText.getManager());
      node.replaceChild(anchorInText, singleLeafElement);
    }
  }

  private static boolean isInsideTagBody(ASTNode place) {
    final ASTNode treeParent = place.getTreeParent();
    if(treeParent instanceof JspXmlRootTag) return true;
    if(treeParent.getElementType() != XmlElementType.XML_TAG
       && treeParent.getElementType() != XmlElementType.HTML_TAG) return false;
    while(place != null){
      if(place.getElementType() == XmlTokenType.XML_TAG_END) return true;
      place = place.getTreePrev();
    }
    return false;
  }

  public boolean addWhitespace(final ASTNode treePrev, final LeafElement whiteSpaceElement) {
    if (isInsideTagBody(treePrev)) {
      addWhitespaceToTagBody(treePrev, whiteSpaceElement);
      return true;
    }

    return false;
  }

  public boolean containsWhitespacesOnly(final ASTNode node) {
    return (node.getElementType() == JspElementType.JSP_XML_TEXT || node.getElementType() == XmlTokenType.XML_DATA_CHARACTERS) &&
           node.getText().trim().length() == 0;
  }
}
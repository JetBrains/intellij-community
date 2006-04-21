package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.xml.util.XmlUtil;

public class CDATAOnAnyEncodedPolicy extends DefaultXmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable charTableByTree) {
    final ASTNode firstChild = text.getNode().getFirstChildNode();
    boolean textAlreadyHasCDATA = firstChild != null && firstChild.getElementType() == XmlElementType.XML_CDATA;
    if ((textAlreadyHasCDATA || XmlUtil.toCode(displayText)) && displayText.length() > 0) {
      final FileElement dummyParent = createCDATAElement(text.getManager(), charTableByTree, displayText);
      return dummyParent.getFirstChildNode();
    }
    else {
      return super.encodeXmlTextContents(displayText, text, charTableByTree);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static FileElement createCDATAElement(final PsiManager manager, final CharTable charTableByTree, final String displayText) {
    final FileElement dummyParent = new DummyHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement cdata = Factory.createCompositeElement(XmlElementType.XML_CDATA);
    TreeUtil.addChildren(dummyParent, cdata);
    TreeUtil.addChildren(
      cdata,
      Factory.createLeafElement(
        XmlTokenType.XML_CDATA_START,
        "<![CDATA[".toCharArray(),
        0, 9, -1,
        dummyParent.getCharTable()));
    TreeUtil.addChildren(
      cdata,
      Factory.createLeafElement(
        XmlTokenType.XML_DATA_CHARACTERS,
        displayText.toCharArray(),
        0, displayText.length(), -1,
        dummyParent.getCharTable()));
    TreeUtil.addChildren(
      cdata,
      Factory.createLeafElement(
        XmlTokenType.XML_CDATA_END,
        "]]>".toCharArray(),
        0, 3, -1,
        dummyParent.getCharTable()));
    dummyParent.acceptTree(new GeneratedMarkerVisitor());
    return dummyParent;
  }
}

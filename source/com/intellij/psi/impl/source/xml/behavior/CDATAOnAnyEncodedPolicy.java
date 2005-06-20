package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.util.CharTable;
import com.intellij.xml.util.XmlUtil;

public class CDATAOnAnyEncodedPolicy extends DefaultXmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, PsiManager manager, CharTable charTableByTree) {
    if(!XmlUtil.toCode(displayText)) return super.encodeXmlTextContents(displayText, manager, charTableByTree);
    final FileElement dummyParent = createCDATAElement(manager, charTableByTree, displayText);

    return dummyParent.getFirstChildNode();
  }

  public static FileElement createCDATAElement(final PsiManager manager, final CharTable charTableByTree, final String displayText) {
    final FileElement dummyParent = new DummyHolder(manager, null, charTableByTree).getTreeElement();
    TreeUtil.addChildren(
      dummyParent,
      Factory.createLeafElement(
        XmlTokenType.XML_CDATA_START,
        "<![CDATA[".toCharArray(),
        0, 9, -1,
        dummyParent.getCharTable()));
    TreeUtil.addChildren(
      dummyParent,
      Factory.createLeafElement(
        XmlTokenType.XML_DATA_CHARACTERS,
        displayText.toCharArray(),
        0, displayText.length(), -1,
        dummyParent.getCharTable()));
    TreeUtil.addChildren(
      dummyParent,
      Factory.createLeafElement(
        XmlTokenType.XML_CDATA_END,
        "]]>".toCharArray(),
        0, 3, -1,
        dummyParent.getCharTable()));
    return dummyParent;
  }
}

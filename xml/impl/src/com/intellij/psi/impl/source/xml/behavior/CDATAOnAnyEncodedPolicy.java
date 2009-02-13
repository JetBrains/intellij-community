package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
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
    final FileElement dummyParent = DummyHolderFactory.createHolder(manager, null, charTableByTree).getTreeElement();
    final CompositeElement cdata = ASTFactory.composite(XmlElementType.XML_CDATA);
    dummyParent.rawAddChildren(cdata);
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_CDATA_START, "<![CDATA["));
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_DATA_CHARACTERS, dummyParent.getCharTable().intern(displayText)));
    cdata.rawAddChildren(ASTFactory.leaf(XmlTokenType.XML_CDATA_END, "]]>"));
    dummyParent.acceptTree(new GeneratedMarkerVisitor());
    return dummyParent;
  }
}

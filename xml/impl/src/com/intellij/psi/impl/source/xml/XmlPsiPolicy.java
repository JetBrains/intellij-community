package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;

public interface XmlPsiPolicy {
  ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable charTableByTree);
}

package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;

public interface XmlPsiPolicy {
  ASTNode encodeXmlTextContents(String displayText);
}

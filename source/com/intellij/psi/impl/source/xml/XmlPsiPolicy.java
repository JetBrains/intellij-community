package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.util.CharTable;
import com.intellij.psi.PsiManager;

public interface XmlPsiPolicy {
  ASTNode encodeXmlTextContents(String displayText, PsiManager manager, CharTable charTableByTree);
}

package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;

public interface XmlPsiPolicy {
  ASTNode encodeXmlTextContents(String displayText, PsiElement text);
}

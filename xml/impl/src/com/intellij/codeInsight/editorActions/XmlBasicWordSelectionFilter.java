package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlElement;

/**
 * @author yole
 */
public class XmlBasicWordSelectionFilter implements Condition<PsiElement> {
  public boolean value(final PsiElement e) {
    return !(e instanceof XmlToken) &&
           !(e instanceof XmlElement);
  }
}

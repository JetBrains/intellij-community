package com.intellij.lang.xml;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlTag;

/**
 * @author ven
 */
public class XmlFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(PsiElement element) {
    return element instanceof XmlElementDecl ||
           element instanceof XmlAttributeDecl ||
           element instanceof XmlTag;
  }

  public String getType(PsiElement element) {
    if (element instanceof XmlElementDecl || element instanceof XmlTag) {
      return "tag";
    } else if (element instanceof XmlAttributeDecl) {
      return "attribute";
    }

    return null;
  }

  public String getHelpId(PsiElement element) {
    return null;
  }

  public String getDescriptiveName(PsiElement element) {
    return ((PsiNamedElement)element).getName();
  }

  public String getNodeText(PsiElement element, boolean useFullName) {
    return ((PsiNamedElement)element).getName();
  }
}

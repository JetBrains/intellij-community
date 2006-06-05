package com.intellij.lang.xml;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.LangBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class XmlFindUsagesProvider implements FindUsagesProvider {

  public boolean canFindUsagesFor(PsiElement element) {
    return element instanceof XmlElementDecl ||
           element instanceof XmlAttributeDecl ||
           element instanceof XmlEntityDecl ||
           element instanceof XmlTag ||
           element instanceof XmlAttributeValue ||
           element instanceof XmlText ||
           (PsiUtil.isInJspFile(element) && element instanceof PsiFile);
  }

  @NotNull
  public String getType(PsiElement element) {
    if (element instanceof XmlElementDecl || element instanceof XmlTag) {
      return LangBundle.message("xml.terms.tag");
    } else if (element instanceof XmlAttributeDecl) {
      return LangBundle.message("xml.terms.attribute");
    } else if (element instanceof XmlAttributeValue) {
      return LangBundle.message("xml.terms.attribute.value");
    } else if (element instanceof XmlEntityDecl) {
      return LangBundle.message("xml.terms.entity");
    } else if (element instanceof XmlText) {
      return "";
    }

    return null;
  }

  public String getHelpId(PsiElement element) {
    return null;
  }

  @NotNull
  public String getDescriptiveName(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    } else {
      return element.getText();
    }
  }

  @NotNull
  public String getNodeText(PsiElement element, boolean useFullName) {
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    } else {
      return element.getText();
    }
  }

  public WordsScanner getWordsScanner() {
    return null;
  }
}

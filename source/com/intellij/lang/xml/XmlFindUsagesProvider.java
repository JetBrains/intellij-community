package com.intellij.lang.xml;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;

/**
 * @author ven
 */
public class XmlFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(PsiElement element) {
    return element instanceof XmlElementDecl ||
           element instanceof XmlAttributeDecl ||
           element instanceof XmlTag ||
           element instanceof XmlAttributeValue;
  }

  public String getType(PsiElement element) {
    if (element instanceof XmlElementDecl || element instanceof XmlTag) {
      return "tag";
    } else if (element instanceof XmlAttributeDecl) {
      return "attribute";
    } else if (element instanceof XmlAttributeValue) {
      return "attribute value";
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

  public boolean mayHaveReferences(IElementType token, short searchContext) {
    if((searchContext & UsageSearchContext.IN_FOREIGN_LANGUAGES) != 0 &&
       (token == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN || token == XmlTokenType.XML_NAME)) return true;
    if((searchContext & UsageSearchContext.IN_PLAIN_TEXT) != 0) return true;
    return false;
  }

  public WordsScanner getWordsScanner() {
    return null;
  }
}

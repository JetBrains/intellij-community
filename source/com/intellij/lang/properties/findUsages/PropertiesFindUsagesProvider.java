package com.intellij.lang.properties.findUsages;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.parsing.PropertiesWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.LangBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertiesFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement;
  }

  public String getHelpId(PsiElement psiElement) {
    return null;
  }

  @NotNull
  public String getType(PsiElement element) {
    if (element instanceof Property) return LangBundle.message("terms.property");
    return "";
  }

  @NotNull
  public String getDescriptiveName(PsiElement element) {
    return ((PsiNamedElement)element).getName();
  }

  @NotNull
  public String getNodeText(PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }

  public boolean mayHaveReferences(IElementType token, final short searchContext) {
    return (searchContext & (UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_PLAIN_TEXT | UsageSearchContext.IN_FOREIGN_LANGUAGES)) != 0;
  }

  public WordsScanner getWordsScanner() {
    return new PropertiesWordsScanner();
  }
}

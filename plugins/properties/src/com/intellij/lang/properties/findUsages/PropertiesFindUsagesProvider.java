package com.intellij.lang.properties.findUsages;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.parsing.PropertiesWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.LangBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.find.impl.HelpID;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertiesFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement;
  }

  public String getHelpId(@NotNull PsiElement psiElement) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    if (element instanceof Property) return LangBundle.message("terms.property");
    return "";
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    return ((PsiNamedElement)element).getName();
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }

  public WordsScanner getWordsScanner() {
    return new PropertiesWordsScanner();
  }
}

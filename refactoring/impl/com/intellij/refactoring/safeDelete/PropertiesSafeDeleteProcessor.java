package com.intellij.refactoring.safeDelete;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PropertiesSafeDeleteProcessor implements SafeDeleteProcessorDelegate {
  public boolean handlesElement(final PsiElement element) {
    return element instanceof PropertiesFile;
  }

  public NonCodeUsageSearchInfo findUsages(final PsiElement element, final PsiElement[] allElementsToDelete, final List<UsageInfo> result) {
    PropertiesFile file = (PropertiesFile) element;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    elements.add(file);
    elements.addAll(file.getProperties());
    for(PsiElement psiElement: elements) {
      SafeDeleteProcessor.findGenericElementUsages(psiElement, result, allElementsToDelete);
    }
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(allElementsToDelete), elements);
  }
}

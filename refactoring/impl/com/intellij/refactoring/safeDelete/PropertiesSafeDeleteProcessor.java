package com.intellij.refactoring.safeDelete;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

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

  public Collection<PsiElement> getAdditionalElementsToDelete(final PsiElement element, final Collection<PsiElement> allElementsToDelete,
                                                              final boolean askUser) {
    return null;
  }

  public Collection<String> findConflicts(final PsiElement element, final PsiElement[] allElementsToDelete) {
    return null;
  }

  public UsageInfo[] preprocessUsages(final Project project, final UsageInfo[] usages) {
    return usages;
  }

  public void prepareForDeletion(final PsiElement element) throws IncorrectOperationException {
  }
}

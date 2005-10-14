package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class SafeDeleteExtendsClassUsageInfo extends SafeDeleteReferenceUsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteExtendsClassUsageInfo");
  private final PsiClass myExtendingClass;

  public SafeDeleteExtendsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass extendingClass) {
    super(reference, refClass, true);
    myExtendingClass = extendingClass;
  }

  public PsiClass getReferencedElement() {
    return (PsiClass)super.getReferencedElement();
  }

  public void deleteElement() throws IncorrectOperationException {
    final PsiElement parent = getElement().getParent();
    LOG.assertTrue(parent instanceof PsiReferenceList);
    final PsiClass refClass = getReferencedElement();
    final PsiReferenceList extendsList = refClass.getExtendsList();
    if (extendsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      final PsiReferenceList listToAddExtends = refClass.isInterface() == myExtendingClass.isInterface() ? myExtendingClass.getExtendsList() :
                                                myExtendingClass.getImplementsList();
      for (int i = 0; i < referenceElements.length; i++) {
        listToAddExtends.add(referenceElements[i]);
      }
    }

    final PsiReferenceList implementsList = refClass.getImplementsList();
    if (implementsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      for (int i = 0; i < referenceElements.length; i++) {
        myExtendingClass.getImplementsList().add(referenceElements[i]);
      }
    }

    getElement().delete();
  }

  public boolean isSafeDelete() {
    final PsiClass refClass = getReferencedElement();
    if (refClass.getExtendsListTypes().length > 0) {
      final PsiReferenceList listToAddExtends = refClass.isInterface() == myExtendingClass.isInterface() ? myExtendingClass.getExtendsList() :
                                                myExtendingClass.getImplementsList();
      if (listToAddExtends == null) return false;
    }

    if (refClass.getImplementsListTypes().length > 0) {
      if (myExtendingClass.getImplementsList() == null) return false;
    }

    return true;
  }
}

package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class SafeDeleteExtendsClassUsageInfo extends SafeDeleteReferenceUsageInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteExtendsClassUsageInfo");
  private final PsiClass myExtendingClass;
  private final PsiSubstitutor mySubstitutor;

  public SafeDeleteExtendsClassUsageInfo(final PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass extendingClass) {
    super(reference, refClass, true);
    myExtendingClass = extendingClass;
    mySubstitutor = TypeConversionUtil.getClassSubstitutor(refClass, myExtendingClass, PsiSubstitutor.EMPTY);
    LOG.assertTrue(mySubstitutor != null);
  }

  public PsiClass getReferencedElement() {
    return (PsiClass)super.getReferencedElement();
  }

  public void deleteElement() throws IncorrectOperationException {
    final PsiElement parent = getElement().getParent();
    LOG.assertTrue(parent instanceof PsiReferenceList);
    final PsiClass refClass = getReferencedElement();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(refClass.getProject()).getElementFactory();

    final PsiReferenceList extendsList = refClass.getExtendsList();
    final PsiReferenceList extendingImplementsList = myExtendingClass.getImplementsList();
    if (extendsList != null) {
      final PsiClassType[] referenceTypes = extendsList.getReferencedTypes();
      final PsiReferenceList listToAddExtends = refClass.isInterface() == myExtendingClass.isInterface() ? myExtendingClass.getExtendsList() : extendingImplementsList;
      for (PsiClassType referenceType : referenceTypes) {
        listToAddExtends.add(elementFactory.createReferenceElementByType((PsiClassType)mySubstitutor.substitute(referenceType)));
      }
    }

    final PsiReferenceList implementsList = refClass.getImplementsList();
    if (implementsList != null) {
      final PsiClassType[] referenceTypes = implementsList.getReferencedTypes();
      for (PsiClassType referenceType : referenceTypes) {
        extendingImplementsList.add(elementFactory.createReferenceElementByType((PsiClassType)mySubstitutor.substitute(referenceType)));
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

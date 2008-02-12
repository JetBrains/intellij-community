package com.intellij.usageView;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiThrowStatement) {
        return UsageViewBundle.message("usage.target.exception");
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiPackage) {
        return ((PsiPackage)element).getQualifiedName();
      }
      else if (element instanceof PsiClass) {
        if (element instanceof PsiAnonymousClass) {
          return LangBundle.message("java.terms.anonymous.class");
        }
        else {
          String ret = ((PsiClass)element).getQualifiedName(); // It happens for local classes
          if (ret == null) {
            ret = ((PsiClass)element).getName();
          }
          return ret;
        }
      }
      else if (element instanceof PsiVariable) {
        return ((PsiVariable)element).getName();
      }
      else if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)element;
        return PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
      }
    }

    return null;
  }
}

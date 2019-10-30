package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;

public class EqualsAndHashCodeCallSuperHandler {

  public static boolean isEqualsAndHashCodeCallSuperDefault(@NotNull PsiElement element) {
    PsiNameValuePair psiNameValuePair = PsiTreeUtil.getParentOfType(element, PsiNameValuePair.class);
    if (psiNameValuePair == null) {
      return false;
    }
    PsiAnnotation psiAnnotation = PsiTreeUtil.getParentOfType(psiNameValuePair, PsiAnnotation.class);
    if (psiAnnotation == null) {
      return false;
    }

    return "callSuper".equals(psiNameValuePair.getName()) && "EqualsAndHashCode".equals(PsiAnnotationSearchUtil.getSimpleNameOf(psiAnnotation));
  }
}

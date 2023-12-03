package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.dataFlow.MethodCallProduceNPESupport;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.InitializationUtils;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

public class LombokMethodCallExpressionNPESupport implements MethodCallProduceNPESupport {
  @Override
  public boolean ignoreMethodCallExpression(@NotNull PsiExpression psiExpression) {
    final PsiField field = PsiTreeUtil.getParentOfType(psiExpression, PsiField.class);
    if (field == null) {
      return false;
    }

    final PsiAnnotation getterAnnotation = PsiAnnotationSearchUtil.findAnnotation(field, LombokClassNames.GETTER);
    final boolean isLazyGetter = null != getterAnnotation && PsiAnnotationUtil.getBooleanAnnotationValue(getterAnnotation, "lazy", false);

    if (isLazyGetter) {
      final PsiReference reference = psiExpression.getReference();
      if (reference != null) {
        PsiElement referencedElement = reference.resolve();
        if (!(referencedElement instanceof PsiField referencedField)) {
          return false;
        }

        PsiClass containingClass = referencedField.getContainingClass();
        if (containingClass != null) {
          return InitializationUtils.isInitializedInConstructors(referencedField, containingClass);
        }
      }
    }
    return isLazyGetter;
  }
}

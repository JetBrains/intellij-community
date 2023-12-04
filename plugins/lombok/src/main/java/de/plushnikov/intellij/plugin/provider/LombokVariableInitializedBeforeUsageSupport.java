package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.daemon.impl.analysis.VariableInitializedBeforeUsageSupport;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;


/**
 * A class that implements the VariableInitializedBeforeUsageSupport interface to provide support for Lombok annotated variables.
 * It checks if a variable expression should be ignored based on Lombok annotations.
 */
public class LombokVariableInitializedBeforeUsageSupport implements VariableInitializedBeforeUsageSupport {
  @Override
  public boolean ignoreVariableExpression(@NotNull PsiReferenceExpression psiExpression, @NotNull PsiVariable psiVariable) {
    final PsiField field = PsiTreeUtil.getParentOfType(psiExpression, PsiField.class);
    if (field == null) {
      return false;
    }

    final PsiAnnotation getterAnnotation = field.getAnnotation(LombokClassNames.GETTER);
    return null != getterAnnotation && PsiAnnotationUtil.getBooleanAnnotationValue(getterAnnotation, "lazy", false);
  }
}

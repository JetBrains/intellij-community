package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class PsiQuickFixFactory {
  public static LocalQuickFix createAddAnnotationQuickFix(@NotNull PsiClass psiClass, @NotNull String annotationFQN, @Nullable String annotationParam) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText("@" + annotationFQN + "(" + StringUtil.notNullize(annotationParam) + ")", psiClass);
    final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();

    return new AddAnnotationFix(annotationFQN, psiClass, attributes);
  }

  public static LocalQuickFix createModifierListFix(@NotNull PsiModifierListOwner owner, @NotNull String modifier, boolean shouldHave, final boolean showContainingClass) {
    return new ModifierFix(owner, modifier, shouldHave, showContainingClass);
  }

  public static LocalQuickFix createNewFieldFix(@NotNull PsiClass psiClass, @NotNull String name, @NotNull PsiType psiType, @Nullable String initializerText, String... modifiers) {
    return new CreateFieldQuickFix(psiClass, name, psiType, initializerText, modifiers);
  }

  public static LocalQuickFix createChangeAnnotationParameterFix(@NotNull PsiAnnotation psiAnnotation, @NotNull String name, @Nullable String newValue) {
    return new ChangeAnnotationParameterQuickFix(psiAnnotation, name, newValue);
  }
}

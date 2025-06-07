package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.RemoveAnnotationQuickFix;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.ig.fixes.ChangeAnnotationParameterQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public final class PsiQuickFixFactory {
  public static @Nullable LocalQuickFix createDeleteAnnotationFix(@NotNull PsiClass psiClass, @Nullable String annotationFQN) {
    if (annotationFQN == null) return null;
    final PsiAnnotation annotation = psiClass.getAnnotation(annotationFQN);
    if (annotation == null) return null;
    return new RemoveAnnotationQuickFix(annotation, psiClass);
  }

  public static LocalQuickFix createAddAnnotationFix(@NotNull PsiClass psiClass,
                                                     @NotNull String annotationFQN,
                                                     @Nullable String annotationParam) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    PsiAnnotation newAnnotation =
      elementFactory.createAnnotationFromText("@" + annotationFQN + "(" + StringUtil.notNullize(annotationParam) + ")", psiClass);
    final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();

    return LocalQuickFix.from(
      new AddAnnotationModCommandAction(annotationFQN, psiClass, attributes, ExternalAnnotationsManager.AnnotationPlace.IN_CODE));
  }

  public static @NotNull LocalQuickFix createAddAnnotationFix(@NotNull String annotationFQN,
                                                              @NotNull PsiModifierListOwner targetForAnnotation) {
    return LocalQuickFix.from(new AddAnnotationModCommandAction(annotationFQN, targetForAnnotation, PsiNameValuePair.EMPTY_ARRAY,
                                                                ExternalAnnotationsManager.AnnotationPlace.IN_CODE));
  }

  public static LocalQuickFix createModifierListFix(@NotNull PsiModifierListOwner owner,
                                                    @NotNull String modifier,
                                                    boolean shouldHave,
                                                    final boolean showContainingClass) {
    return LocalQuickFix.from(new ModifierFix(owner, modifier, shouldHave, showContainingClass));
  }

  public static LocalQuickFix createNewFieldFix(@NotNull PsiClass psiClass,
                                                @NotNull String name,
                                                @NotNull PsiType psiType,
                                                @Nullable String initializerText,
                                                String... modifiers) {
    return LocalQuickFix.from(new CreateFieldQuickFix(psiClass, name, psiType, initializerText, modifiers));
  }

  public static LocalQuickFix createChangeAnnotationParameterFix(@NotNull PsiAnnotation psiAnnotation,
                                                                 @NotNull String name,
                                                                 @Nullable String newValue) {
    return LocalQuickFix.from(new ChangeAnnotationParameterQuickFix(psiAnnotation, name, newValue));
  }
}

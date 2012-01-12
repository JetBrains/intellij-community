package de.plushnikov.intellij.lombok.quickfix;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;

/**
 * @author Plushnikov Michail
 */
public class LombokAnnotationQuickFix10Impl extends AddAnnotationFix implements LombokQuickFix {
  public LombokAnnotationQuickFix10Impl(@NotNull String annotationFQN, @NotNull PsiModifierListOwner modifierListOwner,
                                        @NotNull PsiNameValuePair[] values, @NotNull String... annotationsToRemove) {
    super(annotationFQN, modifierListOwner, values, annotationsToRemove);
  }
}

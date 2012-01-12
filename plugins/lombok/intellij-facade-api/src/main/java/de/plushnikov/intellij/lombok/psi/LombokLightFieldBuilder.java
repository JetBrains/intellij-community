package de.plushnikov.intellij.lombok.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightFieldBuilder extends PsiField {
  LombokLightFieldBuilder withContainingClass(PsiClass psiClass);

  LombokLightFieldBuilder withModifier(@Modifier @NotNull @NonNls String modifier);

  LombokLightFieldBuilder withNavigationElement(PsiElement navigationElement);
}

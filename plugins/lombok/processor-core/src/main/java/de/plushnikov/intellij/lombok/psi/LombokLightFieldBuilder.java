package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightFieldBuilder extends PsiField {
  LombokLightFieldBuilder withContainingClass(PsiClass psiClass);

  LombokLightFieldBuilder withModifier(@NotNull @NonNls String modifier);

  LombokLightFieldBuilder withNavigationElement(PsiElement navigationElement);
}

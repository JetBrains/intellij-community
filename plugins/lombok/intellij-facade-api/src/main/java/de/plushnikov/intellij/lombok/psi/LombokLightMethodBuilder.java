package de.plushnikov.intellij.lombok.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightMethodBuilder extends PsiMethod {

  LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement);

  LombokLightMethodBuilder withContainingClass(@NotNull PsiClass containingClass);

  LombokLightMethodBuilder withModifier(@Modifier @NotNull @NonNls String modifier);

  LombokLightMethodBuilder withMethodReturnType(PsiType returnType);

  LombokLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type);

  LombokLightMethodBuilder withException(@NotNull PsiClassType type);

  LombokLightMethodBuilder withException(@NotNull String fqName);

}

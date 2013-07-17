package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightMethodBuilder extends PsiMethod {

  LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement);

  LombokLightMethodBuilder withContainingClass(@NotNull PsiClass containingClass);

  LombokLightMethodBuilder withModifier(@NotNull @NonNls String modifier);

  LombokLightMethodBuilder withMethodReturnType(PsiType returnType);

  LombokLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type);

  LombokLightMethodBuilder withException(@NotNull PsiClassType type);

  LombokLightMethodBuilder withException(@NotNull String fqName);

  LombokLightMethodBuilder withTypeParameter(@NotNull PsiTypeParameter typeParameter);
}

package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightMethodBuilder extends PsiMethod {
  LombokLightMethodBuilder addModifier(@Modifier @NotNull @NonNls String modifier);

  LombokLightMethodBuilder setMethodReturnType(PsiType returnType);

  LombokLightMethodBuilder addParameter(@NotNull String name, @NotNull PsiType type);

  LombokLightMethodBuilder addException(@NotNull PsiClassType type);

  LombokLightMethodBuilder addException(@NotNull String fqName);

  LombokLightMethodBuilder setConstructor(boolean constructor);

  LombokLightMethodBuilder withNavigationElement(PsiElement navigationElement);

  LombokLightMethodBuilder setContainingClass(@NotNull PsiClass containingClass);
}

package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface LombokLightClassBuilder extends PsiClass {

  LombokLightClassBuilder withModifier(@NotNull @NonNls String modifier);

  LombokLightClassBuilder withContainingClass(@NotNull PsiClass containingClass);

  LombokLightClassBuilder withFields(@NotNull Collection<PsiField> fields);

  LombokLightClassBuilder withMethods(@NotNull Collection<PsiMethod> methods);

  LombokLightClassBuilder withConstructors(@NotNull Collection<PsiMethod> constructors);

  LombokLightClassBuilder withParameterTypes(@NotNull PsiTypeParameterList parameterList);

}

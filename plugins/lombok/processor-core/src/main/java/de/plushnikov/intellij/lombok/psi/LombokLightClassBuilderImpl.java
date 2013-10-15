package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class LombokLightClassBuilderImpl extends LombokLightClass implements LombokLightClassBuilder {

  public LombokLightClassBuilderImpl(@NotNull PsiManager manager, @NotNull String canonicalName, @NotNull String simpleName) {
    super(manager, JavaLanguage.INSTANCE);
    setCanonicalName(canonicalName);
    setName(canonicalName);
    setSimpleName(simpleName);
  }

  @Override
  public LombokLightClassBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    ((LightModifierList) getModifierList()).addModifier(modifier);
    return this;
  }

  @Override
  public LombokLightClassBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  @Override
  public LombokLightClassBuilder withFields(@NotNull Collection<PsiField> fields) {
    setFields(fields.toArray(new PsiField[fields.size()]));
    return this;
  }

  @Override
  public LombokLightClassBuilder withMethods(@NotNull Collection<PsiMethod> methods) {
    setMethods(methods.toArray(new PsiMethod[methods.size()]));
    return this;
  }

  @Override
  public LombokLightClassBuilder withConstructors(@NotNull Collection<PsiMethod> constructors) {
    setConstructors(constructors.toArray(new PsiMethod[constructors.size()]));
    return this;
  }

  @Override
  public LombokLightClassBuilder withParameterTypes(@NotNull PsiTypeParameterList parameterList) {
    setTypeParameterList(parameterList);
    return this;
  }
}

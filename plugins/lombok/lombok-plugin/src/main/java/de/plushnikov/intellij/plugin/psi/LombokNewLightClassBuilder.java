package de.plushnikov.intellij.plugin.psi;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class LombokNewLightClassBuilder extends LombokNewLightClass {

  public LombokNewLightClassBuilder(@NotNull Project project, @NotNull String simpleName, @NotNull String qualifiedName) {
    super(JavaPsiFacade.getElementFactory(project).createClass(simpleName));
    setQualifiedName(qualifiedName);
  }

  public LombokNewLightClassBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public LombokNewLightClassBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  public LombokNewLightClassBuilder withFields(@NotNull Collection<PsiField> fields) {
    setFields(fields.toArray(new PsiField[fields.size()]));
    return this;
  }

  public LombokNewLightClassBuilder withMethods(@NotNull Collection<PsiMethod> methods) {
    setMethods(methods.toArray(new PsiMethod[methods.size()]));
    return this;
  }

  public LombokNewLightClassBuilder withConstructors(@NotNull Collection<PsiMethod> constructors) {
    setConstructors(constructors.toArray(new PsiMethod[constructors.size()]));
    return this;
  }

  public LombokNewLightClassBuilder withParameterTypes(@NotNull PsiTypeParameterList parameterList) {
    setTypeParameterList(parameterList);
    return this;
  }
}

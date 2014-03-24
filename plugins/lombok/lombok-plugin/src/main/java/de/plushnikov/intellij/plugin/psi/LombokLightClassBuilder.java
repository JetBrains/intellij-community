package de.plushnikov.intellij.plugin.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameterList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class LombokLightClassBuilder extends LombokLightClass {

  public LombokLightClassBuilder(@NotNull Project project, @NotNull String simpleName, @NotNull String qualifiedName) {
    super(JavaPsiFacade.getElementFactory(project).createClass(simpleName));
    setQualifiedName(qualifiedName);
  }

  public LombokLightClassBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    getModifierList().addModifier(modifier);
    return this;
  }

  public LombokLightClassBuilder withContainingClass(@NotNull PsiClass containingClass) {
    setContainingClass(containingClass);
    return this;
  }

  public LombokLightClassBuilder withFields(@NotNull Collection<PsiField> fields) {
    setFields(fields.toArray(new PsiField[fields.size()]));
    return this;
  }

  public LombokLightClassBuilder withMethods(@NotNull Collection<PsiMethod> methods) {
    setMethods(methods.toArray(new PsiMethod[methods.size()]));
    return this;
  }

  public LombokLightClassBuilder withConstructors(@NotNull Collection<PsiMethod> constructors) {
    setConstructors(constructors.toArray(new PsiMethod[constructors.size()]));
    return this;
  }

  public LombokLightClassBuilder withParameterTypes(@Nullable PsiTypeParameterList parameterList) {
    setTypeParameterList(parameterList);
    return this;
  }

  public LombokLightClassBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }
}

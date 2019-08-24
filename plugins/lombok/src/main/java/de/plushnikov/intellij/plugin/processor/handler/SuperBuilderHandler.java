package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SuperBuilderHandler extends BuilderHandler {

  private static final String SELF_METHOD = "self";
  private static final String TO_BUILDER_METHOD_NAME = "toBuilder";
  private static final String FILL_VALUES_METHOD_NAME = "$fillValuesFrom";
  private static final String STATIC_FILL_VALUES_METHOD_NAME = "$fillValuesFromInstanceIntoBuilder";
  private static final String INSTANCE_VARIABLE_NAME = "instance";
  private static final String BUILDER_VARIABLE_NAME = "b";

  public SuperBuilderHandler(@NotNull ToStringProcessor toStringProcessor, @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    super(toStringProcessor, noArgsConstructorProcessor);
  }

  @NotNull
  public String getBuilderClassName(@NotNull PsiClass psiClass) {
    return StringUtil.capitalize(psiClass.getName() + BUILDER_CLASS_NAME);
  }

  @NotNull
  public String getBuilderImplClassName(@NotNull PsiClass psiClass) {
    return getBuilderClassName(psiClass) + "Impl";
  }

  public Optional<PsiMethod> createBuilderBasedConstructor(@NotNull PsiClass psiClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation) {
    final String className = psiClass.getName();
    if (null == className) {
      return Optional.empty();
    }

    LombokLightMethodBuilder constructorBuilderBased = new LombokLightMethodBuilder(psiClass.getManager(), className)
      .withConstructor(true)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PROTECTED)
      .withParameter(BUILDER_VARIABLE_NAME, PsiClassUtil.getTypeWithGenerics(builderClass));
    // TODO add body
    return Optional.of(constructorBuilderBased);
  }

  public Optional<PsiMethod> createBuilderMethodIfNecessary(@NotNull PsiClass containingClass, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    return super.createBuilderMethodIfNecessary(containingClass, null, builderPsiClass, psiAnnotation);
  }

  public Optional<PsiMethod> createToBuilderMethodIfNecessary(@NotNull PsiClass containingClass, @NotNull PsiClass builderPsiClass, @NotNull PsiAnnotation psiAnnotation) {
    return super.createToBuilderMethodIfNecessary(containingClass, null, builderPsiClass, psiAnnotation);
  }

  @Override
  public boolean notExistInnerClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass);
    return !PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName).isPresent();
  }

  @NotNull
  public PsiClass createBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation))
      .withModifier(PsiModifier.STATIC);

    //TODO add fields
    //TODO add methods

    return classBuilder;
  }

  @NotNull
  public PsiClass createBuilderImplClass(PsiClass psiClass, PsiAnnotation psiAnnotation) {
    String builderClassName = getBuilderImplClassName(psiClass);
    String builderClassQualifiedName = psiClass.getQualifiedName() + "." + builderClassName;

    final LombokLightClassBuilder classBuilder = new LombokLightClassBuilder(psiClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(psiClass.getTypeParameterList())
      .withModifier(getBuilderOuterAccessVisibility(psiAnnotation))
      .withModifier(PsiModifier.STATIC);

    //TODO add fields
    //TODO add methods

    return classBuilder;
  }
}

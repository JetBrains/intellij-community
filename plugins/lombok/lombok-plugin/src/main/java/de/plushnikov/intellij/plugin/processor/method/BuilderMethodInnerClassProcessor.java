package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.util.BuilderUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuilderMethodInnerClassProcessor extends AbstractMethodProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderMethodInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

    @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRigthType(psiMethod, builder);
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!psiMethod.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
      builder.addError(ErrorMessages.canBeUsedOnStaticMethodOnly(Builder.class));
      result = false;
    }
    return result;
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    PsiClass parentClass = psiMethod.getContainingClass();
    assert parentClass != null;
    final String innerClassSimpleName = BuilderUtil.createBuilderClassNameWithGenerics(psiAnnotation, psiMethod.getReturnType());
    final String innerClassCanonicalName = parentClass.getName() + "." + innerClassSimpleName;
    LombokLightClassBuilder innerClass = new LombokLightClassBuilder(psiMethod.getManager(), innerClassCanonicalName, innerClassSimpleName)
       .withContainingClass(parentClass)
       .withParameterTypes(psiMethod.getTypeParameterList()) // TODO
       .withModifier(PsiModifier.PUBLIC)
       .withModifier(PsiModifier.STATIC);
    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
     .withFields(createFields(psiMethod))
     .withMethods(createMethods(parentClass, innerClass, psiMethod, psiAnnotation));

    target.add(innerClass);
  }

  private Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  private Collection<PsiField> createFields(@NotNull PsiMethod psiMethod) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      fields.add(new LombokLightFieldBuilder(psiMethod.getManager(), psiParameter.getName(), psiParameter.getType())
        .withModifier(PsiModifier.PRIVATE));
    }
    return fields;
  }

  private Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(createSetterMethods(innerClass, psiMethod, psiAnnotation));
    methods.add(createBuildMethod(parentClass, innerClass, psiMethod, psiAnnotation));
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, parentClass));
    return methods;
  }

  private Collection<PsiMethod> createSetterMethods(@NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      assert psiParameter.getName() != null;
      methods.add(new LombokLightMethodBuilder(psiMethod.getManager(), BuilderUtil.createSetterName(psiAnnotation, psiParameter.getName()))
        .withMethodReturnType(BuilderUtil.createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
        .withContainingClass(innerClass)
        .withParameter(psiParameter.getName(), psiParameter.getType())
        .withNavigationElement(innerClass)
        .withModifier(PsiModifier.PUBLIC));
    }
    return methods;
  }

  private PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(parentClass.getManager(), BuilderUtil.createBuildMethodName(psiAnnotation))
       .withMethodReturnType(psiMethod.getReturnType())
       .withContainingClass(innerClass)
       .withNavigationElement(parentClass)
       .withModifier(PsiModifier.PUBLIC);
  }

}

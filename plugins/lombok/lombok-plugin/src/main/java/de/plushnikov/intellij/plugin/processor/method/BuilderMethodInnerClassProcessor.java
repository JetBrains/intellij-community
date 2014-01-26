package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a static method
 * Creates methods for a builder pattern for initializing a class
 *
 * @author Tomasz Kalkosiński
 */
public class BuilderMethodInnerClassProcessor extends AbstractMethodProcessor {

  public BuilderMethodInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

  protected BuilderMethodInnerClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return validateInternal(psiAnnotation, psiMethod, builder, true);
  }

  /**
   * Two processors are used to process @Builder annotation on a static method.
   * Validation process is the same, but in case it fails, errors where added twice.
   * There is a @param shouldAddErrors to avoid this duplication.
   */
  protected boolean validateInternal(PsiAnnotation psiAnnotation, PsiMethod psiMethod, ProblemBuilder builder, boolean shouldAddErrors) {
    return validateAnnotationOnRightType(psiMethod, builder, shouldAddErrors)
        && validateExistingInnerClass(psiMethod, psiAnnotation, builder, shouldAddErrors);
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder, boolean shouldAddErrors) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      if (shouldAddErrors) {
        builder.addError(ErrorMessages.canBeUsedOnStaticMethodOnly(Builder.class));
      }
      return false;
    }
    return true;
  }

  protected boolean validateExistingInnerClass(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder, boolean shouldAddErrors) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, containingClass);
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(containingClass, innerClassSimpleName);
    if (innerClassByName != null) {
      if (shouldAddErrors) {
        builder.addError(String.format("Not generated '%s' class: A class with same name already exists. This feature is not implemented and it's not planned.", innerClassSimpleName));
      }
      return false;
    }
    return true;
  }


  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    PsiClass parentClass = psiMethod.getContainingClass();
    assert parentClass != null;
    final String innerClassSimpleName = BuilderUtil.createBuilderClassNameWithGenerics(psiAnnotation, psiMethod.getReturnType());
    final String innerClassCanonicalName = parentClass.getName() + "." + innerClassSimpleName;
    LombokLightClassBuilder innerClass = new LombokLightClassBuilder(psiMethod.getProject(), innerClassSimpleName, innerClassCanonicalName)
        .withContainingClass(parentClass)
        .withNavigationElement(psiAnnotation)
        .withParameterTypes(psiMethod.getTypeParameterList())
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
      String psiParameterName = psiParameter.getName();
      assert psiParameterName != null;
      fields.add(new LombokLightFieldBuilder(psiMethod.getManager(), psiParameterName, psiParameter.getType())
          .withModifier(PsiModifier.PRIVATE));
    }
    return fields;
  }

  private Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(createSetterMethods(innerClass, psiMethod, psiAnnotation));
    methods.add(createBuildMethod(parentClass, innerClass, psiMethod, psiAnnotation));
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, psiAnnotation));
    return methods;
  }

  private Collection<PsiMethod> createSetterMethods(@NotNull PsiClass innerClass, @NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiParameter psiParameter : psiMethod.getParameterList().getParameters()) {
      String psiParameterName = psiParameter.getName();
      assert psiParameterName != null;
      methods.add(new LombokLightMethodBuilder(psiMethod.getManager(), BuilderUtil.createSetterName(psiAnnotation, psiParameterName))
          .withMethodReturnType(BuilderUtil.createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
          .withContainingClass(innerClass)
          .withParameter(psiParameterName, psiParameter.getType())
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

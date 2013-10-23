package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.ide.util.PackageUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightClass;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokNewLightClassBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.BuilderUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import lombok.Singleton;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a class
 * Creates methods for a builder pattern for initializing a class
 *
 * @author Tomasz Kalkosiński
 */
public class BuilderInnerClassProcessor extends AbstractClassProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

  protected BuilderInnerClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = validateAnnotationOnRightType(psiClass, builder);
    if (result) {
      result = validateExistingInnerClass(psiClass, psiAnnotation, builder);
    }

    if (PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError(ErrorMessages.canBeUsedOnConcreteClassOnly(Singleton.class));
      result = false;
    }

    return result;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(ErrorMessages.canBeUsedOnClassOnly(Singleton.class));
      result = false;
    }
    return result;
  }

  protected boolean validateExistingInnerClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    if (innerClassByName != null) {
      builder.addWarning(String.format("Not generated '%s' class: A class with same name already exists", innerClassSimpleName));
      result = false;
    }

    return result;
  }

  protected void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    processInternNew(psiClass, psiAnnotation, target);
  }

  protected void processInternOld(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final String innerClassCanonicalName = psiClass.getName() + "." + BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);

    // Create inner class only if it doesn't exist.
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    if (innerClassByName == null) {
      LombokLightClassBuilder innerClass = new LombokLightClassBuilder(psiClass.getManager(), innerClassCanonicalName, innerClassSimpleName)
         .withContainingClass(psiClass)
         .withParameterTypes(psiClass.getTypeParameterList())
         .withModifier(PsiModifier.PUBLIC)
         .withModifier(PsiModifier.STATIC);
      innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
         .withFields(createFields(psiClass))
         .withMethods(createMethods(psiClass, innerClass, psiAnnotation));
      target.add(innerClass);
    }
  }

  protected void processInternNew(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final String innerClassQualifiedName = psiClass.getName() + "." + BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);

    // Create inner class only if it doesn't exist.
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    if (innerClassByName == null) {
      LombokNewLightClassBuilder innerClass = new LombokNewLightClassBuilder(psiClass.getProject(), innerClassSimpleName, innerClassQualifiedName)
        .withContainingClass(psiClass)
        .withParameterTypes(psiClass.getTypeParameterList())
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);
      innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
        .withFields(createFields(psiClass))
        .withMethods(createMethods(psiClass, innerClass, psiAnnotation));
      target.add(innerClass);
    }
  }

  protected Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation);
  }

  protected Collection<PsiField> createFields(@NotNull PsiClass psiClass) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        createField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      if (createField) {
        fields.add(new LombokLightFieldBuilder(psiClass.getManager(), psiField.getName(), psiField.getType())
          .withModifier(PsiModifier.PRIVATE));
      }
    }
    return fields;
  }

  protected Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(createFieldMethods(parentClass, innerClass, psiAnnotation));
    methods.add(createBuildMethod(parentClass, innerClass, psiAnnotation));
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, parentClass));
    return methods;
  }

  protected Collection<PsiMethod> createFieldMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiField psiField : parentClass.getFields()) {
      boolean createMethod = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createMethod = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createMethod &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        // skip initialized final fields
        createMethod &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }
      if (createMethod) {
        methods.add(new LombokLightMethodBuilder(psiField.getManager(), BuilderUtil.createSetterName(psiAnnotation, psiField.getName()))
          .withMethodReturnType(BuilderUtil.createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
          .withContainingClass(innerClass)
          .withParameter(psiField.getName(), psiField.getType())
          .withNavigationElement(psiAnnotation)
          .withModifier(PsiModifier.PUBLIC));
      }
    }
    return methods;
  }

  protected PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(parentClass.getManager(), BuilderUtil.createBuildMethodName(psiAnnotation))
       .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(parentClass))
       .withContainingClass(innerClass)
       .withNavigationElement(parentClass)
       .withModifier(PsiModifier.PUBLIC);
  }

}

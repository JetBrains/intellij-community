package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.BuilderUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a class.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Tomasz Kalkosiński
 */
public class BuilderInnerClassProcessor extends AbstractClassProcessor {

  public BuilderInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

  protected BuilderInnerClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateInternal(psiAnnotation, psiClass, builder, true);
  }

  /**
   * Two processors are used to process @Builder annotation on a class.
   * Validation process is the same, but in case it fails, errors where added twice.
   * There is a @param shouldAddErrors to avoid this duplication.
   */
  protected boolean validateInternal(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder, boolean shouldAddErrors) {
    return validateAnnotationOnRightType(psiClass, builder, shouldAddErrors)
        && validateExistingInnerClass(psiClass, psiAnnotation, builder, shouldAddErrors);
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder, boolean shouldAddErrors) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      if (shouldAddErrors) {
        builder.addError(ErrorMessages.canBeUsedOnClassOnly(Builder.class));
      }
      return false;
    }
    return true;
  }

  protected boolean validateExistingInnerClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull ProblemBuilder builder, boolean shouldAddErrors) {
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    if (innerClassByName != null) {
      if (shouldAddErrors) {
        builder.addError("Not generated '%s' class: A class with same name already exists. This feature is not implemented and it's not planned.", innerClassSimpleName);
      }
      return false;
    }
    return true;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final String innerClassQualifiedName = psiClass.getName() + "." + BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);

    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    assert innerClassByName == null; // validation should ensure that

    LombokLightClassBuilder innerClass = new LombokLightClassBuilder(psiClass.getProject(), innerClassSimpleName, innerClassQualifiedName)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withParameterTypes(psiClass.getTypeParameterList())
        .withModifier(PsiModifier.PUBLIC)
        .withModifier(PsiModifier.STATIC);
    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
        .withFields(createFields(psiClass))
        .withMethods(createMethods(psiClass, innerClass, psiAnnotation));
    target.add(innerClass);
  }

  protected Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
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
    methods.addAll(new ToStringProcessor().createToStringMethod(innerClass, psiAnnotation));
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

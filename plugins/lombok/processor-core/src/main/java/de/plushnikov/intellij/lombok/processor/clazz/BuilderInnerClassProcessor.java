package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.BuilderUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.experimental.Builder;
import lombok.Singleton;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok-pg annotation on a class
 * Creates methods for a builder pattern for initializing a class
 * TODO implement me
 *
 * @author Plushnikov Michail
 */
public class BuilderInnerClassProcessor extends AbstractLombokClassProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderInnerClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

  protected BuilderInnerClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = validateAnnotationOnRigthType(psiClass, builder);
    if (result) {
      result = validateExistingMethods(psiClass, builder);
    }

    if (PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError(ErrorMessages.canBeUsedOnConcreteClassOnly(Singleton.class));
      result = false;
    }
    if (PsiClassUtil.hasMultiArgumentConstructor(psiClass)) {
      builder.addError(ErrorMessages.requiresDefaultOrNoArgumentConstructor(Singleton.class));
      result = false;
    }

    return result;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(ErrorMessages.canBeUsedOnClassOnly(Singleton.class));
      result = false;
    }
    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      builder.addWarning(String.format("Not generated '%s'(): A method with same name already exists", METHOD_NAME));
      result = false;
    }

    return result;
  }

  protected void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String innerClassSimpleName = BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);
    final String innerClassCanonicalName = psiClass.getName() + "." + BuilderUtil.createBuilderClassName(psiAnnotation, psiClass);

    // Create inner class only if it doesn't exist.
    final PsiClass innerClassByName = PsiClassUtil.getInnerClassInternByName(psiClass, innerClassSimpleName);
    if (innerClassByName == null) {
      LombokLightClassBuilder innerClass = LombokPsiElementFactory.getInstance().createLightClass(psiClass.getManager(), innerClassCanonicalName, innerClassSimpleName)
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
        fields.add(LombokPsiElementFactory.getInstance().createLightField(psiClass.getManager(), psiField.getName(), psiField.getType())
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
        methods.add(LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), BuilderUtil.createSetterName(psiAnnotation, psiField.getName()))
          .withMethodReturnType(BuilderUtil.createSetterReturnType(psiAnnotation, PsiClassUtil.getTypeWithGenerics(innerClass)))
          .withContainingClass(parentClass)
          .withParameter(psiField.getName(), psiField.getType())
          .withNavigationElement(psiAnnotation)
          .withModifier(PsiModifier.PUBLIC));
      }
    }
    return methods;
  }

  protected PsiMethod createBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementFactory.getInstance().createLightMethod(parentClass.getManager(), BuilderUtil.createBuildMethodName(psiAnnotation))
       .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(parentClass))
       .withContainingClass(innerClass)
       .withNavigationElement(parentClass)
       .withModifier(PsiModifier.PUBLIC);
  }

}

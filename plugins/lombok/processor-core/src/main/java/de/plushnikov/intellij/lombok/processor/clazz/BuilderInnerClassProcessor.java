package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.experimental.Builder;
import lombok.Singleton;
import org.jetbrains.annotations.NotNull;

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
    final String innerClassSimpleName = psiClass.getName() + "Builder";
    final String innerClassCanonicalName = psiClass.getName() + "." + innerClassSimpleName;
    LombokLightClassBuilder innerClass = LombokPsiElementFactory.getInstance().createLightClass(psiClass.getManager(), innerClassCanonicalName, innerClassSimpleName)
       .withContainingClass(psiClass)
       .withModifier(PsiModifier.PUBLIC)
       .withModifier(PsiModifier.STATIC);
    innerClass.withConstructors(createConstructors(innerClass, psiAnnotation))
     .withFields(createFields(psiClass))
     .withMethods(createMethods(psiClass, innerClass));

    target.add(innerClass);
  }

  private Collection<PsiMethod> createConstructors(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    NoArgsConstructorProcessor noArgsConstructorProcessor = new NoArgsConstructorProcessor();
    return noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation);
  }

  private Collection<PsiField> createFields(@NotNull PsiClass psiClass) {
    List<PsiField> fields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createField = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createField &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
      }
      if (createField) {
        fields.add(LombokPsiElementFactory.getInstance().createLightField(psiClass.getManager(), psiField.getName(), psiField.getType())
          .withModifier(PsiModifier.PRIVATE));
      }
    }
    return fields;
  }

  private Collection<PsiMethod> createMethods(@NotNull PsiClass parentClass, @NotNull PsiClass innerClass) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiField psiField : parentClass.getFields()) {
      boolean createMethod = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip static fields.
        createMethod = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields that start with $
        createMethod &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
      }
      if (createMethod) {
        methods.add(LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), psiField.getName())
          .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(innerClass))
          .withContainingClass(parentClass)
          .withParameter(psiField.getName(), psiField.getType())
          .withNavigationElement(parentClass)
          .withModifier(PsiModifier.PUBLIC));
      }
    }
    return methods;

  }
}

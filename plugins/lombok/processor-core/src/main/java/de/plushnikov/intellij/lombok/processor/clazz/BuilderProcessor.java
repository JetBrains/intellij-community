package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.BuilderUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.experimental.Builder;
import lombok.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok-pg annotation on a class
 * Creates methods for a builder pattern for initializing a class
 * TODO implement me
 *
 * @author Plushnikov Michail
 */
public class BuilderProcessor extends AbstractLombokClassProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderProcessor() {
    super(Builder.class, PsiMethod.class);
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
    final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    // Create all args constructor only if there is no declared constructor
    if (definedConstructors.isEmpty()) {
      final AllArgsConstructorProcessor allArgsConstructorProcessor = new AllArgsConstructorProcessor();
      target.addAll(allArgsConstructorProcessor.createRequiredArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation));
    }

    final String builderName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "builder", String.class);
    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiClass.getManager(), BuilderUtil.createBuilderMethodName(psiAnnotation))
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(psiClass.getInnerClasses()[0])) // TODO: It's not good!!
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation);
    method.withModifier(PsiModifier.STATIC);
    method.withModifier(PsiModifier.PUBLIC);
    target.add(method);
  }
}

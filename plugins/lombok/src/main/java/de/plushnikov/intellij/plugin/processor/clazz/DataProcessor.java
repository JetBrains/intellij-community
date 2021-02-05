package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class DataProcessor extends AbstractClassProcessor {

  public DataProcessor() {
    super(PsiMethod.class, LombokClassNames.DATA);
  }

  private ToStringProcessor getToStringProcessor() {
    return ApplicationManager.getApplication().getService(ToStringProcessor.class);
  }

  private NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(NoArgsConstructorProcessor.class);
  }

  private GetterProcessor getGetterProcessor() {
    return ApplicationManager.getApplication().getService(GetterProcessor.class);
  }

  private SetterProcessor getSetterProcessor() {
    return ApplicationManager.getApplication().getService(SetterProcessor.class);
  }

  private EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return ApplicationManager.getApplication().getService(EqualsAndHashCodeProcessor.class);
  }

  private RequiredArgsConstructorProcessor getRequiredArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(RequiredArgsConstructorProcessor.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation =
      PsiAnnotationSearchUtil.findAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
    if (null == equalsAndHashCodeAnnotation) {
      getEqualsAndHashCodeProcessor().validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor", "");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      getRequiredArgsConstructorProcessor().validateBaseClassConstructor(psiClass, builder);
    }

    return validateAnnotationOnRightType(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(LombokBundle.message("inspection.message.data.only.supported.on.class.type"));
      result = false;
    }
    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.GETTER)) {
      target.addAll(getGetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.SETTER)) {
      target.addAll(getSetterProcessor().createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE)) {
      target.addAll(getEqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.TO_STRING)) {
      target.addAll(getToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }

    final boolean hasConstructorWithoutParamaters;
    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor", "");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      target.addAll(
        getRequiredArgsConstructorProcessor().createRequiredArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName, true));
      // if there are no required field, it will already have a default constructor without parameters
      hasConstructorWithoutParamaters = getRequiredArgsConstructorProcessor().getRequiredFields(psiClass).isEmpty();
    }
    else {
      hasConstructorWithoutParamaters = false;
    }

    if (!hasConstructorWithoutParamaters && shouldGenerateExtraNoArgsConstructor(psiClass)) {
      target.addAll(getNoArgsConstructorProcessor().createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }
  }

  private boolean shouldGenerateRequiredArgsConstructor(@NotNull PsiClass psiClass, @Nullable String staticName) {
    boolean result = false;
    // create required constructor only if there are no other constructor annotations
    final boolean notAnnotatedWith = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass,
                                                                                LombokClassNames.NO_ARGS_CONSTRUCTOR,
                                                                                LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR,
                                                                                LombokClassNames.ALL_ARGS_CONSTRUCTOR,
                                                                                LombokClassNames.BUILDER,
                                                                                LombokClassNames.SUPER_BUILDER);
    if (notAnnotatedWith) {
      final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor = getRequiredArgsConstructorProcessor();
      final Collection<PsiField> requiredFields = requiredArgsConstructorProcessor.getRequiredFields(psiClass);

      result = requiredArgsConstructorProcessor.validateIsConstructorNotDefined(
        psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance());
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

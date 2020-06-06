package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class DataProcessor extends AbstractClassProcessor {

  public DataProcessor() {
    super(PsiMethod.class, Data.class);
  }

  private ToStringProcessor getToStringProcessor() {
    return ServiceManager.getService(ToStringProcessor.class);
  }

  private NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return ServiceManager.getService(NoArgsConstructorProcessor.class);
  }

  private GetterProcessor getGetterProcessor() {
    return ServiceManager.getService(GetterProcessor.class);
  }

  private SetterProcessor getSetterProcessor() {
    return ServiceManager.getService(SetterProcessor.class);
  }

  private EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return ServiceManager.getService(EqualsAndHashCodeProcessor.class);
  }

  private RequiredArgsConstructorProcessor getRequiredArgsConstructorProcessor() {
    return ServiceManager.getService(RequiredArgsConstructorProcessor.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, EqualsAndHashCode.class);
    if (null == equalsAndHashCodeAnnotation) {
      getEqualsAndHashCodeProcessor().validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      getRequiredArgsConstructorProcessor().validateBaseClassConstructor(psiClass, builder);
    }

    return validateAnnotationOnRightType(psiClass, builder);
  }


  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@Data' is only supported on a class type");
      result = false;
    }
    return result;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll(getGetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll(getSetterProcessor().createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll(getEqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
      target.addAll(getToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }

    final boolean hasConstructorWithoutParamaters;
    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      target.addAll(getRequiredArgsConstructorProcessor().createRequiredArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName));
      // if there are no required field, it will already have a default constructor without parameters
      hasConstructorWithoutParamaters = getRequiredArgsConstructorProcessor().getRequiredFields(psiClass).isEmpty();
    } else {
      hasConstructorWithoutParamaters = false;
    }

    if (!hasConstructorWithoutParamaters && shouldGenerateNoArgsConstructor(psiClass, getRequiredArgsConstructorProcessor())) {
      target.addAll(getNoArgsConstructorProcessor().createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }
  }

  private boolean shouldGenerateRequiredArgsConstructor(@NotNull PsiClass psiClass, @Nullable String staticName) {
    boolean result = false;
    // create required constructor only if there are no other constructor annotations
    @SuppressWarnings("unchecked") final boolean notAnnotatedWith = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class,
      RequiredArgsConstructor.class, AllArgsConstructor.class, Builder.class, SuperBuilder.class);
    if (notAnnotatedWith) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      final Collection<PsiField> requiredFields = getRequiredArgsConstructorProcessor().getRequiredFields(psiClass);

      result = getRequiredArgsConstructorProcessor().validateIsConstructorNotDefined(
        psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance());
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

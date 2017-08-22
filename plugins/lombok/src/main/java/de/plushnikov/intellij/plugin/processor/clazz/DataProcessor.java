package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class DataProcessor extends AbstractClassProcessor {

  private final GetterProcessor getterProcessor;
  private final SetterProcessor setterProcessor;
  private final EqualsAndHashCodeProcessor equalsAndHashCodeProcessor;
  private final ToStringProcessor toStringProcessor;
  private final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor;

  public DataProcessor(GetterProcessor getterProcessor, SetterProcessor setterProcessor, EqualsAndHashCodeProcessor equalsAndHashCodeProcessor,
                       ToStringProcessor toStringProcessor, RequiredArgsConstructorProcessor requiredArgsConstructorProcessor) {
    super(PsiMethod.class, Data.class);
    this.getterProcessor = getterProcessor;
    this.setterProcessor = setterProcessor;
    this.equalsAndHashCodeProcessor = equalsAndHashCodeProcessor;
    this.toStringProcessor = toStringProcessor;
    this.requiredArgsConstructorProcessor = requiredArgsConstructorProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, EqualsAndHashCode.class);
    if (null == equalsAndHashCodeAnnotation) {
      equalsAndHashCodeProcessor.validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      requiredArgsConstructorProcessor.validateBaseClassConstructor(psiClass, builder);
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
      target.addAll(getterProcessor.createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll(setterProcessor.createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll(equalsAndHashCodeProcessor.createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
      target.addAll(toStringProcessor.createToStringMethod(psiClass, psiAnnotation));
    }

    final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
    if (shouldGenerateRequiredArgsConstructor(psiClass, staticName)) {
      target.addAll(requiredArgsConstructorProcessor.createRequiredArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName));
    }
  }

  private boolean shouldGenerateRequiredArgsConstructor(@NotNull PsiClass psiClass, @Nullable String staticName) {
    boolean result = false;
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class, RequiredArgsConstructor.class, AllArgsConstructor.class,
      Builder.class, lombok.experimental.Builder.class)) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      // and only if there are no any other constructors!
      if (definedConstructors.isEmpty()) {
        final Collection<PsiField> requiredFields = requiredArgsConstructorProcessor.getRequiredFields(psiClass);

        result = requiredArgsConstructorProcessor.validateIsConstructorNotDefined(
          psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance());
      }
    }
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

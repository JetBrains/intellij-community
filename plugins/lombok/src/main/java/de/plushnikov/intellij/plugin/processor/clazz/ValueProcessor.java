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
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author twillouer
 */
public class ValueProcessor extends AbstractClassProcessor {

  private final GetterProcessor getterProcessor;
  private final EqualsAndHashCodeProcessor equalsAndHashCodeProcessor;
  private final ToStringProcessor toStringProcessor;
  private final AllArgsConstructorProcessor allArgsConstructorProcessor;
  private final NoArgsConstructorProcessor noArgsConstructorProcessor;

  public ValueProcessor(@NotNull GetterProcessor getterProcessor,
                        @NotNull EqualsAndHashCodeProcessor equalsAndHashCodeProcessor,
                        @NotNull ToStringProcessor toStringProcessor,
                        @NotNull AllArgsConstructorProcessor allArgsConstructorProcessor,
                        @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    super(PsiMethod.class, Value.class);

    this.getterProcessor = getterProcessor;
    this.equalsAndHashCodeProcessor = equalsAndHashCodeProcessor;
    this.toStringProcessor = toStringProcessor;
    this.allArgsConstructorProcessor = allArgsConstructorProcessor;
    this.noArgsConstructorProcessor = noArgsConstructorProcessor;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, EqualsAndHashCode.class);
    if (null == equalsAndHashCodeAnnotation) {
      equalsAndHashCodeProcessor.validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    return validateAnnotationOnRightType(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@Value' is only supported on a class type");
      result = false;
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll(getterProcessor.createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll(equalsAndHashCodeProcessor.createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
      target.addAll(toStringProcessor.createToStringMethod(psiClass, psiAnnotation));
    }
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class, RequiredArgsConstructor.class, AllArgsConstructor.class,
      lombok.Builder.class)) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
      final Collection<PsiField> requiredFields = allArgsConstructorProcessor.getAllFields(psiClass);

      if (allArgsConstructorProcessor.validateIsConstructorNotDefined(psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance())) {
        target.addAll(allArgsConstructorProcessor.createAllArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName, requiredFields));
      }
    }

    if (shouldGenerateNoArgsConstructor(psiClass, allArgsConstructorProcessor)) {
      target.addAll(noArgsConstructorProcessor.createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addClassAnnotation(result, psiClass, lombok.experimental.NonFinal.class.getName(), lombok.experimental.PackagePrivate.class.getName());
    addFieldsAnnotation(result, psiClass, lombok.experimental.NonFinal.class.getName(), lombok.experimental.PackagePrivate.class.getName());
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

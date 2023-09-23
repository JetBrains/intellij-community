package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author twillouer
 */
@Service
public final class ValueProcessor extends AbstractClassProcessor {

  public ValueProcessor() {
    super(PsiMethod.class, LombokClassNames.VALUE);
  }

  private static ToStringProcessor getToStringProcessor() {
    return ApplicationManager.getApplication().getService(ToStringProcessor.class);
  }

  private static AllArgsConstructorProcessor getAllArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(AllArgsConstructorProcessor.class);
  }

  private static NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(NoArgsConstructorProcessor.class);
  }

  private static GetterProcessor getGetterProcessor() {
    return ApplicationManager.getApplication().getService(GetterProcessor.class);
  }

  private static EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return ApplicationManager.getApplication().getService(EqualsAndHashCodeProcessor.class);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    Collection<String> result = new ArrayList<>();

    final String staticConstructorName = getStaticConstructorNameValue(psiAnnotation);
    if(StringUtil.isNotEmpty(staticConstructorName)) {
      result.add(staticConstructorName);
    }
    result.addAll(getNoArgsConstructorProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getToStringProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getEqualsAndHashCodeProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));
    result.addAll(getGetterProcessor().getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation));

    return result;
  }

  private static String getStaticConstructorNameValue(@NotNull PsiAnnotation psiAnnotation) {
    return PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor", "");
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    validateAnnotationOnRightType(psiClass, builder);

    if (builder.deepValidation()) {
      if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE)) {
        getEqualsAndHashCodeProcessor().validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
      }
    }
    return builder.success();
  }

  private static void validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addErrorMessage("inspection.message.value.only.supported.on.class.type");
      builder.markFailed();
    }
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {

    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.GETTER)) {
      target.addAll(getGetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE)) {
      target.addAll(getEqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.TO_STRING)) {
      target.addAll(getToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.NO_ARGS_CONSTRUCTOR,
                                                   LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.ALL_ARGS_CONSTRUCTOR,
                                                   LombokClassNames.BUILDER)) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      final String staticName = getStaticConstructorNameValue(psiAnnotation);
      final Collection<PsiField> requiredFields = AbstractConstructorClassProcessor.getAllFields(psiClass);

      if (getAllArgsConstructorProcessor().validateIsConstructorNotDefined(psiClass, staticName, requiredFields,
                                                                           new ProblemProcessingSink())) {
        target.addAll(
          getAllArgsConstructorProcessor().createAllArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName, requiredFields,
                                                                    true));
      }
    }

    if (shouldGenerateExtraNoArgsConstructor(psiClass)) {
      target.addAll(getNoArgsConstructorProcessor().createNoArgsConstructor(psiClass, PsiModifier.PRIVATE, psiAnnotation, true));
    }
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addClassAnnotation(result, psiClass, LombokClassNames.NON_FINAL, LombokClassNames.PACKAGE_PRIVATE);
    addFieldsAnnotation(result, psiClass, LombokClassNames.NON_FINAL, LombokClassNames.PACKAGE_PRIVATE);
    return result;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author twillouer
 */
public class ValueProcessor extends AbstractClassProcessor {

  public ValueProcessor() {
    super(PsiMethod.class, LombokClassNames.VALUE);
  }

  private ToStringProcessor getToStringProcessor() {
    return ApplicationManager.getApplication().getService(ToStringProcessor.class);
  }

  private AllArgsConstructorProcessor getAllArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(AllArgsConstructorProcessor.class);
  }

  private NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(NoArgsConstructorProcessor.class);
  }

  private GetterProcessor getGetterProcessor() {
    return ApplicationManager.getApplication().getService(GetterProcessor.class);
  }

  private EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return ApplicationManager.getApplication().getService(EqualsAndHashCodeProcessor.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final PsiAnnotation equalsAndHashCodeAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, LombokClassNames.EQUALS_AND_HASHCODE);
    if (null == equalsAndHashCodeAnnotation) {
      getEqualsAndHashCodeProcessor().validateCallSuperParamExtern(psiAnnotation, psiClass, builder);
    }

    return validateAnnotationOnRightType(psiClass, builder);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(LombokBundle.message("inspection.message.value.only.supported.on.class.type"));
      result = false;
    }
    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.GETTER)) {
      target.addAll(getGetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.EQUALS_AND_HASHCODE)) {
      target.addAll(this.getEqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.TO_STRING)) {
      target.addAll(getToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.NO_ARGS_CONSTRUCTOR, LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.ALL_ARGS_CONSTRUCTOR, LombokClassNames.BUILDER)) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor", "");
      final Collection<PsiField> requiredFields = getAllArgsConstructorProcessor().getAllFields(psiClass);

      if (getAllArgsConstructorProcessor().validateIsConstructorNotDefined(psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance())) {
        target.addAll(getAllArgsConstructorProcessor().createAllArgsConstructor(psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName, requiredFields, true));
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

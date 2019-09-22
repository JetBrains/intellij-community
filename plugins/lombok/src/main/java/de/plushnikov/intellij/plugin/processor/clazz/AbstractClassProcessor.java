package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigKey;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemNewBuilder;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AbstractConstructorClassProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base lombok processor class for class annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractClassProcessor extends AbstractProcessor implements ClassProcessor {

  protected AbstractClassProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                                   @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  protected AbstractClassProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                                   @NotNull Class<? extends Annotation> supportedAnnotationClass,
                                   @NotNull Class<? extends Annotation> equivalentAnnotationClass) {
    super(supportedClass, supportedAnnotationClass, equivalentAnnotationClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    List<? super PsiElement> result = Collections.emptyList();

    PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, getSupportedAnnotationClasses());
    if (null != psiAnnotation) {
      if (supportAnnotationVariant(psiAnnotation) && validate(psiAnnotation, psiClass, ProblemEmptyBuilder.getInstance())) {
        result = new ArrayList<>();
        generatePsiElements(psiClass, psiAnnotation, result);
      }
    }
    return result;
  }

  @NotNull
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    Collection<PsiAnnotation> result = new ArrayList<>();
    PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, getSupportedAnnotationClasses());
    if (null != psiAnnotation) {
      result.add(psiAnnotation);
    }
    return result;
  }

  protected void addClassAnnotation(Collection<PsiAnnotation> result, @NotNull PsiClass psiClass, String... annotationFQNs) {
    PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, annotationFQNs);
    if (null != psiAnnotation) {
      result.add(psiAnnotation);
    }
  }

  protected void addFieldsAnnotation(Collection<PsiAnnotation> result, @NotNull PsiClass psiClass, String... annotationFQNs) {
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, annotationFQNs);
      if (null != psiAnnotation) {
        result.add(psiAnnotation);
      }
    }
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();
    // check first for fields, methods and filter it out, because PsiClass is parent of all annotations and will match other parents too
    @SuppressWarnings("unchecked")
    PsiElement psiElement = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class, PsiMethod.class, PsiClass.class);
    if (psiElement instanceof PsiClass) {
      ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder();
      validate(psiAnnotation, (PsiClass) psiElement, problemNewBuilder);
      result = problemNewBuilder.getProblems();
    }

    return result;
  }

  protected Optional<PsiClass> getSupportedParentClass(@NotNull PsiClass psiClass) {
    final PsiElement parentElement = psiClass.getParent();
    if (parentElement instanceof PsiClass && !(parentElement instanceof LombokLightClassBuilder)) {
      return Optional.of((PsiClass) parentElement);
    }
    return Optional.empty();
  }

  @Nullable
  protected PsiAnnotation getSupportedAnnotation(@NotNull PsiClass psiParentClass) {
    return PsiAnnotationSearchUtil.findAnnotation(psiParentClass, getSupportedAnnotationClasses());
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder);

  protected abstract void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target);

  void validateOfParam(PsiClass psiClass, ProblemBuilder builder, PsiAnnotation psiAnnotation, Collection<String> ofProperty) {
    for (String fieldName : ofProperty) {
      if (!StringUtil.isEmptyOrSpaces(fieldName)) {
        PsiField fieldByName = psiClass.findFieldByName(fieldName, false);
        if (null == fieldByName) {
          final String newPropertyValue = calcNewPropertyValue(ofProperty, fieldName);
          builder.addWarning(String.format("The field '%s' does not exist", fieldName),
            PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "of", newPropertyValue));
        }
      }
    }
  }

  void validateExcludeParam(PsiClass psiClass, ProblemBuilder builder, PsiAnnotation psiAnnotation, Collection<String> excludeProperty) {
    for (String fieldName : excludeProperty) {
      if (!StringUtil.isEmptyOrSpaces(fieldName)) {
        PsiField fieldByName = psiClass.findFieldByName(fieldName, false);
        if (null == fieldByName) {
          final String newPropertyValue = calcNewPropertyValue(excludeProperty, fieldName);
          builder.addWarning(String.format("The field '%s' does not exist", fieldName),
            PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", newPropertyValue));
        } else {
          if (fieldName.startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER) || fieldByName.hasModifierProperty(PsiModifier.STATIC)) {
            final String newPropertyValue = calcNewPropertyValue(excludeProperty, fieldName);
            builder.addWarning(String.format("The field '%s' would have been excluded anyway", fieldName),
              PsiQuickFixFactory.createChangeAnnotationParameterFix(psiAnnotation, "exclude", newPropertyValue));
          }
        }
      }
    }
  }

  private String calcNewPropertyValue(Collection<String> allProperties, String fieldName) {
    String result = null;
    if (!allProperties.isEmpty() && (allProperties.size() > 1 || !allProperties.contains(fieldName))) {
      result = allProperties.stream().filter(((Predicate<String>) fieldName::equals).negate())
        .collect(Collectors.joining("\",\"", "{\"", "\"}"));
    }
    return result;
  }

  boolean shouldGenerateNoArgsConstructor(@NotNull PsiClass psiClass, @NotNull AbstractConstructorClassProcessor argsConstructorProcessor) {
    boolean result = configDiscovery.getBooleanLombokConfigProperty(ConfigKey.NO_ARGS_CONSTRUCTOR_EXTRA_PRIVATE, psiClass);
    if (result) {
      result = !PsiClassUtil.hasSuperClass(psiClass);
    }
    if (result) {
      result = PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class);
    }
    if (result && PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, AllArgsConstructor.class)) {
      result = argsConstructorProcessor.getAllFields(psiClass).isEmpty();
    }
    if (result && PsiAnnotationSearchUtil.isAnnotatedWith(psiClass, RequiredArgsConstructor.class)) {
      result = argsConstructorProcessor.getRequiredFields(psiClass).isEmpty();
    }
    return result;
  }

  boolean readCallSuperAnnotationOrConfigProperty(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ConfigKey configKey) {
    final boolean result;
    final Boolean declaredAnnotationValue = PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, "callSuper");
    if (null == declaredAnnotationValue) {
      final String configProperty = configDiscovery.getStringLombokConfigProperty(configKey, psiClass);
      result = PsiClassUtil.hasSuperClass(psiClass) && "CALL".equalsIgnoreCase(configProperty);
    } else {
      result = declaredAnnotationValue;
    }
    return result;
  }
}

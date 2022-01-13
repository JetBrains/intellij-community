package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemNewBuilder;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Base lombok processor class for field annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractFieldProcessor extends AbstractProcessor implements FieldProcessor {

  AbstractFieldProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                         @NotNull String supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  AbstractFieldProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                         @NotNull String supportedAnnotationClass,
                         @NotNull String equivalentAnnotationClass) {
    super(supportedClass, supportedAnnotationClass, equivalentAnnotationClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    List<? super PsiElement> result = new ArrayList<>();
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        if (possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation, psiField)
            && validate(psiAnnotation, psiField, ProblemEmptyBuilder.getInstance())) {

          generatePsiElements(psiField, psiAnnotation, result);
        }
      }
    }
    return result;
  }

  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField) {
    return true;
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    List<PsiAnnotation> result = new ArrayList<>();
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        result.add(psiAnnotation);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();

    PsiField psiField = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class);
    if (null != psiField) {
      ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder();
      validate(psiAnnotation, psiField, problemNewBuilder);
      result = problemNewBuilder.getProblems();
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder);

  protected void validateOnXAnnotations(@NotNull PsiAnnotation psiAnnotation,
                                        @NotNull PsiField psiField,
                                        @NotNull ProblemBuilder builder,
                                        @NotNull String parameterName) {
    final @NotNull List<PsiAnnotation> copyableAnnotations = copyableAnnotations(psiField, LombokCopyableAnnotations.BASE_COPYABLE);

    if (!copyableAnnotations.isEmpty()) {
      final Iterable<String> onXAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, parameterName);
      List<String> copyableAnnotationsFQNs = ContainerUtil.map(copyableAnnotations, PsiAnnotation::getQualifiedName);
      for (String copyableAnnotationFQN : copyableAnnotationsFQNs) {
        for (String onXAnnotation : onXAnnotations) {
          if (onXAnnotation.startsWith(copyableAnnotationFQN)) {
            builder.addError(LombokBundle.message("inspection.message.annotation.copy.duplicate", copyableAnnotationFQN));
          }
        }
      }
    }

    if (psiField.isDeprecated()) {
      final Iterable<String> onMethodAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, "onMethod");
      if (StreamSupport.stream(onMethodAnnotations.spliterator(), false).anyMatch(CommonClassNames.JAVA_LANG_DEPRECATED::equals)) {
        builder.addError(LombokBundle.message("inspection.message.annotation.copy.duplicate", CommonClassNames.JAVA_LANG_DEPRECATED));
      }
    }
  }

  protected abstract void generatePsiElements(@NotNull PsiField psiField,
                                              @NotNull PsiAnnotation psiAnnotation,
                                              @NotNull List<? super PsiElement> target);

  protected boolean validateExistingMethods(@NotNull PsiField psiField,
                                            @NotNull ProblemBuilder builder,
                                            boolean isGetter) {

    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      final String fieldName = psiField.getName();
      final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      filterByAccessorName(isGetter, isBoolean, accessorsInfo, fieldName, classMethods);

      filterToleratedElements(classMethods);

      Collection<String> allAccessorsNames = isGetter ? LombokUtils.toAllGetterNames(accessorsInfo, fieldName, isBoolean)
                                                      : LombokUtils.toAllSetterNames(accessorsInfo, fieldName, isBoolean);
      for (String methodName : allAccessorsNames) {
        if (PsiMethodUtil.hasSimilarMethod(classMethods, methodName, isGetter ? 0 : 1)) {
          final String accessorName = isGetter ? LombokUtils.getGetterName(psiField)
                                                   : LombokUtils.getSetterName(psiField, isBoolean);

          builder.addWarning(LombokBundle.message("inspection.message.not.generated.s.method.with.similar.name.s.already.exists"),
                             accessorName, methodName);
          result = false;
        }
      }
    }
    return result;
  }

  private static void filterByAccessorName(boolean isGetter,
                                           boolean isBoolean,
                                           AccessorsInfo accessorsInfo,
                                           String fieldName,
                                           Collection<PsiMethod> classMethods) {
    String baseFieldName = StringUtil.trimStart(accessorsInfo.removePrefix(fieldName), "is");
    classMethods.removeIf(method -> {
      String methodName = method.getName();
      if (!StringUtil.containsIgnoreCase(methodName, baseFieldName)) {
        return true;
      }

      return !accessorsInfo.isFluent() && !isAccessorName(isGetter, isBoolean, methodName);
    });
  }

  private static boolean isAccessorName(boolean isGetter, boolean isBoolean, String methodName) {
    if (isGetter) {
      return isBoolean && methodName.startsWith("is") || methodName.startsWith("get");
    }
    return methodName.startsWith("set");
  }
}

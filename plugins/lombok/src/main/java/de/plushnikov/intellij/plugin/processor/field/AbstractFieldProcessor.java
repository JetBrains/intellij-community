package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.*;
import com.intellij.psi.impl.RecordAugmentProvider;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.problem.ProblemValidationSink;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokCopyableAnnotations;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    Collection<PsiField> fields = psiClass.isRecord() ? RecordAugmentProvider.getFieldAugments(psiClass)
                                                      : PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : fields) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        if (possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation, psiField)
            && validate(psiAnnotation, psiField, new ProblemProcessingSink())) {

          generatePsiElements(psiField, psiAnnotation, result, nameHint);
        }
      }
    }
    return result;
  }

  private boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                 @NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField) {
    if (null == nameHint) {
      return true;
    }
    final Collection<String> namesOfGeneratedElements = getNamesOfPossibleGeneratedElements(psiClass, psiAnnotation, psiField);
    return namesOfGeneratedElements.isEmpty() || namesOfGeneratedElements.contains(nameHint);
  }

  protected abstract Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass,
                                                                            @NotNull PsiAnnotation psiAnnotation,
                                                                            @NotNull PsiField psiField);

  protected abstract void generatePsiElements(@NotNull PsiField psiField,
                                              @NotNull PsiAnnotation psiAnnotation,
                                              @NotNull List<? super PsiElement> target,
                                              @Nullable String nameHint);

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
      ProblemValidationSink problemNewBuilder = new ProblemValidationSink();
      validate(psiAnnotation, psiField, problemNewBuilder);
      result = problemNewBuilder.getProblems();
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder);

  protected void validateOnXAnnotations(@NotNull PsiAnnotation psiAnnotation,
                                        @NotNull PsiField psiField,
                                        @NotNull ProblemSink problemSink,
                                        @NotNull String parameterName) {
    if (problemSink.deepValidation()) {
      final @NotNull List<PsiAnnotation> copyableAnnotations =
        LombokCopyableAnnotations.BASE_COPYABLE.collectCopyableAnnotations(psiField, psiField.getContainingClass());

      if (!copyableAnnotations.isEmpty()) {
        final Iterable<String> onXAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, parameterName);
        List<String> copyableAnnotationsFQNs = ContainerUtil.map(copyableAnnotations, PsiAnnotation::getQualifiedName);
        for (String copyableAnnotationFQN : copyableAnnotationsFQNs) {
          for (String onXAnnotation : onXAnnotations) {
            if (onXAnnotation.startsWith(copyableAnnotationFQN)) {
              problemSink.addErrorMessage("inspection.message.annotation.copy.duplicate", copyableAnnotationFQN);
            }
          }
        }
      }

      if (psiField.isDeprecated()) {
        final Iterable<String> onMethodAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, "onMethod");
        if (ContainerUtil.exists(onMethodAnnotations, CommonClassNames.JAVA_LANG_DEPRECATED::equals)) {
          problemSink.addErrorMessage("inspection.message.annotation.copy.duplicate", CommonClassNames.JAVA_LANG_DEPRECATED);
        }
      }
    }
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField,
                                            @NotNull ProblemSink builder,
                                            boolean isGetter) {

    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      //cache signatures to speed up editing of big files, where getName goes in psi tree
      List<MethodSignatureBackedByPsiMethod> ownSignatures = CachedValuesManager.getCachedValue(psiClass, () -> {
        List<MethodSignatureBackedByPsiMethod> signatures =
          ContainerUtil.map(PsiClassUtil.collectClassMethodsIntern(psiClass),
                            m -> MethodSignatureBackedByPsiMethod.create(m, PsiSubstitutor.EMPTY));
        return new CachedValueProvider.Result<>(signatures, PsiModificationTracker.MODIFICATION_COUNT);
      });

      final List<MethodSignatureBackedByPsiMethod> classMethods = new ArrayList<>(ownSignatures);

      final boolean isBoolean = PsiTypes.booleanType().equals(psiField.getType());
      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);
      final String fieldName = psiField.getName();

      final Collection<String> accessorNames = isGetter ? LombokUtils.toAllGetterNames(accessorsInfo, fieldName, isBoolean)
                                                        : LombokUtils.toAllSetterNames(accessorsInfo, fieldName, isBoolean);
      classMethods.removeIf(m -> !accessorNames.contains(m.getName()));

      final int paramCount = isGetter ? 0 : 1;
      classMethods.removeIf(m -> checkLombokParameterCount(m, paramCount));

      classMethods.removeIf(definedMethod -> PsiAnnotationSearchUtil.isAnnotatedWith(definedMethod.getMethod(), LombokClassNames.TOLERATE));

      if (!classMethods.isEmpty()) {
        final String accessorName = isGetter ? LombokUtils.toGetterName(accessorsInfo, fieldName, isBoolean)
                                             : LombokUtils.toSetterName(accessorsInfo, fieldName, isBoolean);
        builder.addWarningMessage("inspection.message.not.generated.s.method.with.similar.name.s.already.exists",
                                  accessorName, classMethods.get(0));
        return false;
      }
    }
    return true;
  }

  private static boolean checkLombokParameterCount(MethodSignatureBackedByPsiMethod m, int paramCount) {
    final int methodParameterCount = m.getParameterTypes().length;
    if (methodParameterCount != paramCount) {
      if (methodParameterCount > 0) {
        if (m.getMethod().getParameterList().getParameters()[methodParameterCount - 1].isVarArgs()) {
          return paramCount < (methodParameterCount - 1);
        }
      }
      return true;
    }
    return false;
  }
}

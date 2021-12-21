package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
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
    Collection<PsiField> fields = psiClass.isRecord() ? collectRecordFields(psiClass) : PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : fields) {
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

    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      //cache signatures to speedup editing of big files, where getName goes in psi tree
      List<MethodSignatureBackedByPsiMethod> ownSignatures = CachedValuesManager.getCachedValue(psiClass, () -> {
        List<MethodSignatureBackedByPsiMethod> signatures =
          ContainerUtil.map(PsiClassUtil.collectClassMethodsIntern(psiClass),
                            m -> MethodSignatureBackedByPsiMethod.create(m, PsiSubstitutor.EMPTY));
        return new CachedValueProvider.Result<>(signatures, PsiModificationTracker.MODIFICATION_COUNT);
      });

      final List<MethodSignatureBackedByPsiMethod> classMethods = new ArrayList<>(ownSignatures);

      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      final String fieldName = psiField.getName();
      String accessorName = isGetter ? LombokUtils.toGetterName(accessorsInfo, fieldName, isBoolean)
                                     : LombokUtils.toSetterName(accessorsInfo, fieldName, isBoolean);
      int paramCount = isGetter ? 0 : 1;
      classMethods.removeIf(m -> m.getParameterTypes().length != paramCount || !accessorName.equals(m.getName()));

      classMethods.removeIf(definedMethod -> PsiAnnotationSearchUtil.isAnnotatedWith(definedMethod.getMethod(), LombokClassNames.TOLERATE));

      if (!classMethods.isEmpty()) {
        builder.addWarning(LombokBundle.message("inspection.message.not.generated.s.method.with.similar.name.s.already.exists"),
                           accessorName, accessorName);
        return false;
      }
    }
    return true;
  }

  private static Collection<PsiField> collectRecordFields(@NotNull PsiClass psiClass) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory();
    Set<PsiField> fields = Arrays.stream(psiClass.getRecordComponents())
      .filter(c -> c.getName() != null && c.getTypeElement() != null)
      .map(c -> {
        String type = c.getTypeElement().getText();
        if (type.endsWith("...")) {
          type = type.substring(0, type.length() - 3) + "[]";
        }
        PsiField field = factory.createFieldFromText(String.format("private final %s %s;", type, c.getName()), psiClass);
        return new LightRecordField(psiClass.getManager(), field, psiClass, c);
      })
      .collect(Collectors.toSet());

    fields.addAll(((PsiExtensibleClass) psiClass).getOwnFields());
    return fields;
  }
}

package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class DataProcessor extends AbstractLombokClassProcessor {

  public DataProcessor() {
    super(Data.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    validateCallSuperParam(psiAnnotation, psiClass, builder, "equals/hashCode");

    return validateAnnotationOnRigthType(psiClass, builder);
  }

  protected void validateCallSuperParam(PsiAnnotation psiAnnotation, PsiClass psiClass, ProblemBuilder builder, String generatedMethodName) {
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      if (PsiClassUtil.hasSuperClass(psiClass)) {
        builder.addWarning("Generating " + generatedMethodName + " implementation but without a call to superclass, " +
            "even though this class does not extend java.lang.Object." +
            "If this is intentional, add '@EqualsAndHashCode(callSuper=false)' to your type.",
            PsiQuickFixFactory.createAddAnnotationQuickFix(psiClass, "lombok.EqualsAndHashCode", "callSuper=false"));
      }
    }
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@Data' is only supported on a class type");
      result = false;
    }
    return result;
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll((Collection<? extends Psi>) new GetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll((Collection<? extends Psi>) new SetterProcessor().createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll((Collection<? extends Psi>) new EqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
      target.addAll((Collection<? extends Psi>) new ToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class, RequiredArgsConstructor.class, AllArgsConstructor.class)) {
      final PsiMethod[] definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      // and only if there are no any other constructors!
      if (0 == definedConstructors.length) {
        final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor = new RequiredArgsConstructorProcessor();

        final String staticName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "staticConstructor", String.class);
        final Collection<PsiField> requiredFields = requiredArgsConstructorProcessor.getRequiredFields(psiClass);

        if (requiredArgsConstructorProcessor.validateIsConstructorDefined(psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance())) {
          target.addAll((Collection<? extends Psi>) requiredArgsConstructorProcessor.createRequiredArgsConstructor(
              psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName));
        }
      }
    }
  }

}

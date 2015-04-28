package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
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
public class DataProcessor extends AbstractClassProcessor {

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

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, Getter.class)) {
      target.addAll(new GetterProcessor().createFieldGetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, Setter.class)) {
      target.addAll(new SetterProcessor().createFieldSetters(psiClass, PsiModifier.PUBLIC));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, EqualsAndHashCode.class)) {
      target.addAll(new EqualsAndHashCodeProcessor().createEqualAndHashCode(psiClass, psiAnnotation));
    }
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, ToString.class)) {
      target.addAll(new ToStringProcessor().createToStringMethod(psiClass, psiAnnotation));
    }
    // create required constructor only if there are no other constructor annotations
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, NoArgsConstructor.class, RequiredArgsConstructor.class, AllArgsConstructor.class)) {
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      filterToleratedElements(definedConstructors);

      // and only if there are no any other constructors!
      if (definedConstructors.isEmpty()) {
        final RequiredArgsConstructorProcessor requiredArgsConstructorProcessor = new RequiredArgsConstructorProcessor();

        final String staticName = PsiAnnotationUtil.getStringAnnotationValue(psiAnnotation, "staticConstructor");
        final Collection<PsiField> requiredFields = requiredArgsConstructorProcessor.getRequiredFields(psiClass);

        if (requiredArgsConstructorProcessor.validateIsConstructorDefined(psiClass, staticName, requiredFields, ProblemEmptyBuilder.getInstance())) {
          target.addAll(requiredArgsConstructorProcessor.createRequiredArgsConstructor(
              psiClass, PsiModifier.PUBLIC, psiAnnotation, staticName));
        }
      }
    }
  }

}

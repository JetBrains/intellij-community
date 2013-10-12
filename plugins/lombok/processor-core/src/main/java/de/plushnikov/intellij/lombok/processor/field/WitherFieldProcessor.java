package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.LombokUtils;
import de.plushnikov.intellij.lombok.StringUtils;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.quickfix.PsiQuickFixFactory;
import de.plushnikov.intellij.lombok.util.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.Wither;
import org.jetbrains.annotations.NotNull;

import java.lang.Class;
import java.lang.annotation.Annotation;
import java.util.List;

import static de.plushnikov.intellij.lombok.util.LombokProcessorUtil.getMethodModifier;
import static de.plushnikov.intellij.lombok.util.PsiAnnotationUtil.isAnnotatedWith;
import static de.plushnikov.intellij.lombok.util.PsiElementUtil.typesAreEquivalent;
import static java.lang.String.format;

public class WitherFieldProcessor extends AbstractLombokFieldProcessor {

  public WitherFieldProcessor() {
    super(Wither.class, PsiMethod.class);
  }

  protected WitherFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {

    boolean valid = validNonStatic(psiField, psiAnnotation, builder);
    valid &= validHasConstructor(psiField, builder);
    valid &= validIsWitherUnique(psiField, psiAnnotation, builder);

    return valid;
  }

  @Override
  protected void processIntern(PsiField psiField, PsiAnnotation psiAnnotation, List<? super PsiElement> target) {
    String methodModifier = getMethodModifier(psiAnnotation);
    if (methodModifier != null) {
      AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      PsiMethod method = createWitherMethod(psiField, methodModifier, accessorsInfo);
      if (method != null) {
        target.add(method);
      }
    }
  }

  private String witherName(String fieldName) {
    final String suffix = fieldName.startsWith("is") && Character.isUpperCase(fieldName.charAt(2)) ?
        fieldName.substring(2) :
        fieldName;
    return "with" + StringUtils.capitalize(suffix);
  }

  private String secondWitherName(String fieldName) {
    return "with" + StringUtils.capitalize(fieldName);
  }

  private boolean validNonStatic(PsiField field, PsiAnnotation annotation, @NotNull final ProblemBuilder builder) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      builder.addError(format("'@%s' on static field is not allowed", annotation.getQualifiedName()),
          PsiQuickFixFactory.createModifierListFix(field, PsiModifier.STATIC, false, false));
      return false;
    }
    return true;
  }

  private boolean hasParam(PsiField field, PsiMethod psiMethod) {
    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      if (typesAreEquivalent(param.getType(), field.getType())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasRightConstructor(PsiField field) {
    PsiClass psiClass = field.getContainingClass();
    if (psiClass != null) {
      for (PsiMethod psiMethod : PsiClassUtil.collectClassConstructorIntern(psiClass)) {
        if (hasParam(field, psiMethod)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean validHasConstructor(final PsiField field, @NotNull final ProblemBuilder builder) {

    if (PsiAnnotationUtil.isAnnotatedWith(field.getContainingClass(), AllArgsConstructor.class)) {
      return true;
    }
    if (hasRightConstructor(field)) {
      return true;
    }
    final boolean hasRequiredArgsConstAnot = PsiAnnotationUtil.isAnnotatedWith(field.getContainingClass(), RequiredArgsConstructor.class);
    final boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);
    final boolean hasNonNullAnot = isAnnotatedWith(field, NonNull.class);

    if (hasRequiredArgsConstAnot && (isFinal || hasNonNullAnot)) {
      return true;
    } else {
      builder.addWarning(format("Compilation will fail : no constructor with a parameter of type '%s' was found",
          field.getType().getCanonicalText()));
      return false;
    }
  }

  private boolean validIsWitherUnique(PsiField field, PsiAnnotation annotation, @NotNull final ProblemBuilder builder) {

    if (field.getName() != null && field.getContainingClass() != null) {
      if (PsiMethodUtil.hasSimilarMethod(PsiClassUtil.collectClassMethodsIntern(field.getContainingClass()), witherName(field.getName()), 1)
          || PsiMethodUtil.hasSimilarMethod(PsiClassUtil.collectClassMethodsIntern(field.getContainingClass()), secondWitherName(field.getName()), 1)) {
        builder.addWarning(
            format("No '@%s' generated : a method named '%s' taking one parameter already exists",
                annotation.getQualifiedName(),
                witherName(field.getName())));
        return false;
      }
    }
    return true;
  }

  public PsiMethod createWitherMethod(@NotNull PsiField psiField, @NotNull String methodModifier, @NotNull AccessorsInfo accessorsInfo) {
    if (psiField != null && psiField.getManager() != null && psiField.getType() != null && psiField.getContainingClass() != null) {
      PsiType returnType = PsiClassUtil.getTypeWithGenerics(psiField.getContainingClass());
      if (returnType != null) {
        final LombokLightMethodBuilder method =
            LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), witherName(accessorsInfo.removePrefix(psiField.getName())))
                .withMethodReturnType(returnType)
                .withContainingClass(psiField.getContainingClass())
                .withParameter(psiField.getName(), psiField.getType())
                .withNavigationElement(psiField)
                .withModifier(methodModifier);
        copyAnnotations(psiField, method.getModifierList(),
            LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
        return method;
      }
    }
    return null;
  }
}

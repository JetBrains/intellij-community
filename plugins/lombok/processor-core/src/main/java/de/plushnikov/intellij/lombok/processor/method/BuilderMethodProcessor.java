package de.plushnikov.intellij.lombok.processor.method;

import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import de.plushnikov.intellij.lombok.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Singleton;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class BuilderMethodProcessor extends AbstractLombokMethodProcessor {

  public static final String METHOD_NAME = "getInstance";

  public BuilderMethodProcessor() {
    super(Builder.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRigthType(psiMethod, builder);
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!psiMethod.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
      builder.addError(ErrorMessages.canBeUsedOnStaticMethodOnly(Builder.class));
      result = false;
    }
    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      builder.addWarning(String.format("Not generated '%s'(): A method with same name already exists", METHOD_NAME));
      result = false;
    }

    return result;
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String builderName = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "builder", String.class);
    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiMethod.getManager(), builderName != null ? builderName : "builder")
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(psiMethod.getContainingClass().getInnerClasses()[0])) // TODO: It's not good!!
        .withContainingClass(psiMethod.getContainingClass())
        .withNavigationElement(psiAnnotation);
    method.withModifier(PsiModifier.STATIC);
    method.withModifier(PsiModifier.PUBLIC);
    target.add(method);
  }
}

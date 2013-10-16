package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.ErrorMessages;
import de.plushnikov.intellij.plugin.util.BuilderUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class BuilderMethodProcessor extends AbstractMethodProcessor {

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
    LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiMethod.getManager(), BuilderUtil.createBuilderMethodName(psiAnnotation))
        .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(psiMethod.getContainingClass().getInnerClasses()[0])) // TODO: It's not good!!
        .withContainingClass(psiMethod.getContainingClass())
        .withNavigationElement(psiAnnotation);
    method.withModifier(PsiModifier.STATIC);
    method.withModifier(PsiModifier.PUBLIC);
    target.add(method);
  }
}

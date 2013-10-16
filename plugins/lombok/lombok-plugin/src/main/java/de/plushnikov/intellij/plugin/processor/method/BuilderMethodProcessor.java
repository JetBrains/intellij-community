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

/**
 * Inspect and validate @Builder lombok annotation on a static method
 * Creates methods for a builder pattern for initializing a class
 *
 * @author Tomasz Kalkosiński
 */
public class BuilderMethodProcessor extends BuilderMethodInnerClassProcessor {

  public BuilderMethodProcessor() {
    super(Builder.class, PsiMethod.class);
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass containingClass = psiMethod.getContainingClass();
    assert containingClass != null;

    String innerClassName = BuilderUtil.createBuilderClassNameWithGenerics(psiAnnotation, psiMethod.getReturnType());
    PsiClass innerClassByName = PsiClassUtil.getInnerClassByName(containingClass, innerClassName);
    assert innerClassByName != null;

    final String builderMethodName = BuilderUtil.createBuilderMethodName(psiAnnotation);
    if (!PsiMethodUtil.hasMethodByName(PsiClassUtil.collectClassMethodsIntern(containingClass), builderMethodName)) {
      LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiMethod.getManager(), builderMethodName)
          .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(innerClassByName))
          .withContainingClass(containingClass)
          .withNavigationElement(psiAnnotation);
      method.withModifier(PsiModifier.STATIC);
      method.withModifier(PsiModifier.PUBLIC);
      target.add(method);
    }

  }
}

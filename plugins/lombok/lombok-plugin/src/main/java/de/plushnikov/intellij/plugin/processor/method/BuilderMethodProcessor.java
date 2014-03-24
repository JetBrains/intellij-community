package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a static method.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderMethodProcessor extends AbstractMethodProcessor {

  private final BuilderHandler builderHandler = new BuilderHandler();
  private final AllArgsConstructorProcessor allArgsConstructorProcessor = new AllArgsConstructorProcessor();

  public BuilderMethodProcessor() {
    super(Builder.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiAnnotation, psiMethod, builder);
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (null != psiClass) {

      final PsiType psiBuilderType = builderHandler.getBuilderType(psiMethod, psiClass);
      final PsiClass psiBuilderClass = PsiTypesUtil.getPsiClass(psiBuilderType);
      final String builderClassName = builderHandler.getBuilderClassName(null == psiBuilderClass ? psiClass : psiBuilderClass, psiAnnotation);
      final PsiClass builderClass = PsiClassUtil.getInnerClassByName(psiClass, builderClassName);
      if (null != builderClass) {
        target.add(builderHandler.createBuilderMethod(psiClass, builderClass, psiAnnotation));
      }
    }
  }
}

package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Inspect and validate @Builder lombok annotation on a method
 * Creates inner class for a builder pattern
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderClassMethodProcessor extends AbstractMethodProcessor {

  public BuilderClassMethodProcessor() {
    super(PsiClass.class, LombokClassNames.BUILDER);
  }

  /**
   * Checks the given annotation to be supported 'Builder' annotation
   */
  @Override
  protected boolean checkAnnotationFQN(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod) {
    return BuilderHandler.checkAnnotationFQN(psiClass, psiAnnotation, psiMethod);
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod) {
    if (null == nameHint) {
      return true;
    }

    final String innerBuilderClassName = BuilderHandler.getBuilderClassName(psiClass, psiAnnotation, psiMethod);
    return Objects.equals(nameHint, innerBuilderClassName);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemSink problemSink) {
    return getHandler().validate(psiMethod, psiAnnotation, problemSink);
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (null != psiClass) {
      final BuilderHandler builderHandler = getHandler();
      builderHandler.createBuilderClassIfNotExist(psiClass, psiMethod, psiAnnotation).ifPresent(target::add);
    }
  }

  private static BuilderHandler getHandler() {
    return new BuilderHandler();
  }
}

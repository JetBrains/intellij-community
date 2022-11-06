package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
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

  public BuilderMethodProcessor() {
    super(PsiMethod.class, LombokClassNames.BUILDER);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemSink problemSink) {
    // we skip validation here, because it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiMethod, psiAnnotation, builder);
  }

  /**
   * Checks the given annotation to be supported 'Builder' annotation
   */
  @Override
  protected boolean checkAnnotationFQN(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod) {
    return BuilderHandler.checkAnnotationFQN(psiClass, psiAnnotation, psiMethod);
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod,
                               @NotNull PsiAnnotation psiAnnotation,
                               @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    final BuilderHandler builderHandler = getHandler();
    if (null != psiClass) {

      PsiClass builderClass = builderHandler.getExistInnerBuilderClass(psiClass, psiMethod, psiAnnotation).orElse(null);
      if (null == builderClass) {
        // have to create full class (with all methods) here, or auto completion doesn't work
        builderClass = builderHandler.createBuilderClass(psiClass, psiMethod, psiAnnotation);
      }

      target.addAll(
        builderHandler.createBuilderDefaultProviderMethodsIfNecessary(psiClass, null, builderClass, psiAnnotation));

      builderHandler.createBuilderMethodIfNecessary(psiClass, psiMethod, builderClass, psiAnnotation)
        .ifPresent(target::add);

      builderHandler.createToBuilderMethodIfNecessary(psiClass, psiMethod, builderClass, psiAnnotation)
        .ifPresent(target::add);
    }
  }

  private static BuilderHandler getHandler() {
    return new BuilderHandler();
  }
}

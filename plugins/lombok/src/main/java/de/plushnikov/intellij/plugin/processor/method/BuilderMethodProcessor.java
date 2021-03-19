package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
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
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    // we skip validation here, bacause it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiMethod, psiAnnotation, builder);
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    final BuilderHandler builderHandler = ApplicationManager.getApplication().getService(BuilderHandler.class);
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
}

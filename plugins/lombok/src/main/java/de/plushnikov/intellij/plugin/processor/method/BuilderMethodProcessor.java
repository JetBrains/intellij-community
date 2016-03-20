package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.Builder;
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

  private final BuilderHandler builderHandler;

  @SuppressWarnings({"deprecation", "unchecked"})
  public BuilderMethodProcessor(@NotNull BuilderHandler builderHandler) {
    super(PsiMethod.class, Builder.class, lombok.experimental.Builder.class);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiMethod, psiAnnotation, builder);
  }

  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (null != psiClass) {

      final PsiType psiBuilderType = builderHandler.getBuilderType(psiClass, psiMethod);

      final String builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
      PsiClass builderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
      if (null == builderClass) {
        builderClass = builderHandler.createBuilderClass(psiClass, psiMethod, psiAnnotation);
      }

      builderHandler.createBuilderMethodIfNecessary(target, psiClass, psiMethod, builderClass, psiAnnotation);

      builderHandler.createToBuilderMethodIfNecessary(target, psiClass, psiMethod, builderClass, psiAnnotation);
    }
  }
}

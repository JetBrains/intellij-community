package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a class
 * Creates inner class for a builder pattern
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderClassProcessor extends AbstractClassProcessor {

  private final BuilderHandler builderHandler;

  public BuilderClassProcessor(@NotNull BuilderHandler builderHandler) {
    super(PsiClass.class, Builder.class);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    builderHandler.createBuilderClassIfNotExist(psiClass, null, psiAnnotation).ifPresent(target::add);
  }
}

package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import lombok.experimental.Builder;
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

  private final BuilderHandler builderHandler = new BuilderHandler();

  public BuilderClassProcessor() {
    super(Builder.class, PsiClass.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiAnnotation, psiClass, true, builder);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    target.add(builderHandler.createBuilderClass(psiClass, psiAnnotation));
  }
}

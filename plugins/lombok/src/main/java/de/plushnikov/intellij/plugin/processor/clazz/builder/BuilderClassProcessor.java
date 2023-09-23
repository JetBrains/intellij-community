package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.components.Service;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a class
 * Creates inner class for a builder pattern
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
@Service
public final class BuilderClassProcessor extends AbstractClassProcessor {

  public BuilderClassProcessor() {
    super(PsiClass.class, LombokClassNames.BUILDER);
  }

  private static BuilderHandler getBuilderHandler() {
    return new BuilderHandler();
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String innerBuilderClassName = BuilderHandler.getBuilderClassName(psiClass, psiAnnotation, null);
    return Collections.singleton(innerBuilderClassName);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    return getBuilderHandler().validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    getBuilderHandler().createBuilderClassIfNotExist(psiClass, null, psiAnnotation).ifPresent(target::add);
  }
}

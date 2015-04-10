package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Builder lombok annotation on a class.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderProcessor extends AbstractClassProcessor {

  private final BuilderHandler builderHandler = new BuilderHandler();
  private final AllArgsConstructorProcessor allArgsConstructorProcessor = new AllArgsConstructorProcessor();

  public BuilderProcessor() {
    this(Builder.class);
  }

  protected BuilderProcessor(@NotNull Class<? extends Annotation> builderClass) {
    super(builderClass, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationUtil.isNotAnnotatedWith(psiClass, AllArgsConstructor.class)) {
      // Create all args constructor only if there is no declared constructors and no lombok constructor annotations
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      if (definedConstructors.isEmpty()) {
        target.addAll(allArgsConstructorProcessor.createAllArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation));
      }
    }

    final PsiType psiBuilderType = builderHandler.getBuilderType(psiClass);

    final String builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
    PsiClass builderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
    if (null == builderClass) {
      builderClass = builderHandler.createBuilderClass(psiClass, psiAnnotation);
    }
    target.add(builderHandler.createBuilderMethod(psiClass, null, builderClass, psiAnnotation));
  }
}

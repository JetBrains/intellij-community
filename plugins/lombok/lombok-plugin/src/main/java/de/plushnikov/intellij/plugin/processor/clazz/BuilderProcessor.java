package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.Builder;
import org.jetbrains.annotations.NotNull;

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
    super(Builder.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiAnnotation, psiClass, false, builder);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
    // Create all args constructor only if there is no declared constructors
    if (definedConstructors.isEmpty()) {
      target.addAll(allArgsConstructorProcessor.createAllArgsConstructor(psiClass, PsiModifier.DEFAULT, psiAnnotation));
    }

    final PsiType psiBuilderType = PsiClassUtil.getTypeWithGenerics(psiClass);

    final String builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
    final PsiClass builderClass = PsiClassUtil.getInnerClassByName(psiClass, builderClassName);
    if (null != builderClass) {
      target.add(builderHandler.createBuilderMethod(psiClass, builderClass, psiAnnotation));
    }
  }
}

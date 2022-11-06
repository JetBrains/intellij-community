package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Inspect and validate @Builder lombok annotation on a class.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderProcessor extends AbstractClassProcessor {

  static final String SINGULAR_CLASS = LombokClassNames.SINGULAR;
  static final String BUILDER_DEFAULT_CLASS = LombokClassNames.BUILDER_DEFAULT;

  public BuilderProcessor() {
    super(PsiMethod.class, LombokClassNames.BUILDER);
  }

  private static BuilderHandler getBuilderHandler() {
    return new BuilderHandler();
  }

  private static AllArgsConstructorProcessor getAllArgsConstructorProcessor() {
    return ApplicationManager.getApplication().getService(AllArgsConstructorProcessor.class);
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    if (null == nameHint) {
      return true;
    }

    boolean possibleMatchFound = Objects.equals(nameHint, psiClass.getName());
    if (!possibleMatchFound) {
      possibleMatchFound = Objects.equals(nameHint, BuilderHandler.TO_BUILDER_METHOD_NAME);
      if (!possibleMatchFound) {
        final String builderMethodName = getBuilderHandler().getBuilderMethodName(psiAnnotation);
        possibleMatchFound = Objects.equals(nameHint, builderMethodName);
      }
    }
    return possibleMatchFound;
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, SINGULAR_CLASS, BUILDER_DEFAULT_CLASS);
    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    // we skip validation here, because it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.ALL_ARGS_CONSTRUCTOR,
      LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.NO_ARGS_CONSTRUCTOR)) {
      // Create all args constructor only if there is no declared constructors and no lombok constructor annotations
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      if (definedConstructors.isEmpty()) {
        target.addAll(getAllArgsConstructorProcessor().createAllArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation));
      }
    }

    final BuilderHandler builderHandler = getBuilderHandler();
    final String builderClassName = BuilderHandler.getBuilderClassName(psiClass, psiAnnotation, null);
    final PsiClass builderClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderClass) {
      target.addAll(
        builderHandler.createBuilderDefaultProviderMethodsIfNecessary(psiClass, null, builderClass, psiAnnotation));

      builderHandler.createBuilderMethodIfNecessary(psiClass, null, builderClass, psiAnnotation)
        .ifPresent(target::add);

      builderHandler.createToBuilderMethodIfNecessary(psiClass, null, builderClass, psiAnnotation)
        .ifPresent(target::add);
    }
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

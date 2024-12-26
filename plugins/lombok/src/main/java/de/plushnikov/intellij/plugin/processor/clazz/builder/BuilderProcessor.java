package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
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

/**
 * Inspect and validate @Builder lombok annotation on a class.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public final class BuilderProcessor extends AbstractClassProcessor {

  static final String SINGULAR_CLASS = LombokClassNames.SINGULAR;
  static final String BUILDER_DEFAULT_CLASS = LombokClassNames.BUILDER_DEFAULT;

  public BuilderProcessor() {
    super(PsiMethod.class, LombokClassNames.BUILDER);
  }

  private static BuilderHandler getBuilderHandler() {
    return new BuilderHandler();
  }

  private static AllArgsConstructorProcessor getAllArgsConstructorProcessor() {
    return LombokProcessorManager.getInstance().getAllArgsConstructorProcessor();
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@NotNull String nameHint,
                                                   @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation) {
    return nameHint.equals(BuilderHandler.TO_BUILDER_METHOD_NAME) ||
           nameHint.equals(psiClass.getName()) ||
           nameHint.equals(getBuilderHandler().getBuilderMethodName(psiAnnotation));
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final BuilderHandler builderHandler = getBuilderHandler();

    final String builderMethodName = builderHandler.getBuilderMethodName(psiAnnotation);
    final String constructorName = StringUtil.notNullize(psiClass.getName());
    return List.of(builderMethodName, BuilderHandler.TO_BUILDER_METHOD_NAME, constructorName);
  }

  @Override
  public @NotNull Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addJacksonizedAnnotation(psiClass, result);
    addFieldsAnnotation(result, psiClass, SINGULAR_CLASS, BUILDER_DEFAULT_CLASS);
    return result;
  }

  private static void addJacksonizedAnnotation(@NotNull PsiClass psiClass, Collection<PsiAnnotation> result) {
    final PsiAnnotation jacksonizedAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, LombokClassNames.JACKSONIZED);
    if(null!=jacksonizedAnnotation) {
      result.add(jacksonizedAnnotation);
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    // we skip validation here, because it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target,
                                     @Nullable String nameHint) {
    if (!psiClass.isRecord()) {
      if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, LombokClassNames.ALL_ARGS_CONSTRUCTOR,
        LombokClassNames.REQUIRED_ARGS_CONSTRUCTOR, LombokClassNames.NO_ARGS_CONSTRUCTOR)) {
        // Create all args constructor only if there is no declared constructors and no lombok constructor annotations
        final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
        if (definedConstructors.isEmpty()) {
          target.addAll(getAllArgsConstructorProcessor().createAllArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation));
        }
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

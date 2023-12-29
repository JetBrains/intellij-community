package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @SuperBuilder lombok annotation on a class.
 * Creates methods for a @SuperBuilder pattern for initializing a class.
 *
 * @author Michail Plushnikov
 */
public final class SuperBuilderProcessor extends AbstractClassProcessor {

  public SuperBuilderProcessor() {
    super(PsiMethod.class, LombokClassNames.SUPER_BUILDER);
  }

  private static SuperBuilderHandler getBuilderHandler() {
    return new SuperBuilderHandler();
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

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addJacksonizedAnnotation(psiClass, result);
    addFieldsAnnotation(result, psiClass, BuilderProcessor.SINGULAR_CLASS, BuilderProcessor.BUILDER_DEFAULT_CLASS);
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
    SuperBuilderHandler builderHandler = getBuilderHandler();
    final String builderClassName = builderHandler.getBuilderClassName(psiClass);
    final PsiClass builderBaseClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderBaseClass) {
      final PsiClassType psiTypeBaseWithGenerics = SuperBuilderHandler.getTypeWithWildcardsForSuperBuilderTypeParameters(builderBaseClass);

      builderHandler.createBuilderBasedConstructor(psiClass, builderBaseClass, psiAnnotation, psiTypeBaseWithGenerics)
        .ifPresent(target::add);

      target.addAll(
        builderHandler.createBuilderDefaultProviderMethodsIfNecessary(psiClass, null, builderBaseClass, psiAnnotation));

      // skip generation of builder methods, if class is abstract
      if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final String builderImplClassName = builderHandler.getBuilderImplClassName(psiClass);
        final PsiClass builderImplClass = psiClass.findInnerClassByName(builderImplClassName, false);

        if (null != builderImplClass) {
          builderHandler.createBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
            .ifPresent(target::add);

          SuperBuilderHandler.createToBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
            .ifPresent(target::add);
        }
      }
    }
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}

package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @SuperBuilder lombok annotation on a class.
 * Creates methods for a @SuperBuilder pattern for initializing a class.
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderProcessor extends AbstractClassProcessor {

  public SuperBuilderProcessor() {
    super(PsiMethod.class, LombokClassNames.SUPER_BUILDER);
  }

  protected SuperBuilderHandler getBuilderHandler() {
    return ApplicationManager.getApplication().getService(SuperBuilderHandler.class);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, BuilderProcessor.SINGULAR_CLASS, BuilderProcessor.BUILDER_DEFAULT_CLASS);
    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    // we skip validation here, because it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    SuperBuilderHandler builderHandler = getBuilderHandler();
    final String builderClassName = builderHandler.getBuilderClassName(psiClass);
    final PsiClass builderBaseClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderBaseClass) {
      final PsiClassType psiTypeBaseWithGenerics = builderHandler.getTypeWithWildcardsForSuperBuilderTypeParameters(builderBaseClass);

      builderHandler.createBuilderBasedConstructor(psiClass, builderBaseClass, psiAnnotation, psiTypeBaseWithGenerics)
        .ifPresent(target::add);

      // skip generation of builder methods, if class is abstract
      if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final String builderImplClassName = builderHandler.getBuilderImplClassName(psiClass);
        final PsiClass builderImplClass = psiClass.findInnerClassByName(builderImplClassName, false);

        if (null != builderImplClass) {
          builderHandler.createBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
            .ifPresent(target::add);

          builderHandler.createToBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
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

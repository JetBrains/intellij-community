package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.experimental.SuperBuilder;
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

  private final BuilderHandler builderHandler;

  public SuperBuilderProcessor(@NotNull SuperBuilderHandler superBuilderHandler) {
    super(PsiMethod.class, SuperBuilder.class);
    this.builderHandler = superBuilderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_SUPER_BUILDER_ENABLED);
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

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String builderClassName = ((SuperBuilderHandler) builderHandler).getBuilderClassName(psiClass);
    final PsiClass builderBaseClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderBaseClass) {
      final PsiClassType psiTypeBaseWithGenerics = ((SuperBuilderHandler) builderHandler).getTypeWithWildcardsForSuperBuilderTypeParameters(builderBaseClass);

      ((SuperBuilderHandler) builderHandler).createBuilderBasedConstructor(psiClass, builderBaseClass, psiAnnotation, psiTypeBaseWithGenerics)
        .ifPresent(target::add);

      // skip generation of builder methods, if class is abstract
      if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final String builderImplClassName = ((SuperBuilderHandler) builderHandler).getBuilderImplClassName(psiClass);
        final PsiClass builderImplClass = psiClass.findInnerClassByName(builderImplClassName, false);

        if (null != builderImplClass) {
          ((SuperBuilderHandler) builderHandler).createBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
            .ifPresent(target::add);

          ((SuperBuilderHandler) builderHandler).createToBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation, psiTypeBaseWithGenerics)
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

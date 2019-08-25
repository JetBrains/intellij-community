package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @SuperBuilder lombok annotation on a class.
 * Creates methods for a @SuperBuilder pattern for initializing a class.
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderProcessor extends BuilderProcessor {

  private final SuperBuilderHandler superBuilderHandler;

  public SuperBuilderProcessor(@NotNull ConfigDiscovery configDiscovery,
                               @NotNull AllArgsConstructorProcessor allArgsConstructorProcessor,
                               @NotNull SuperBuilderHandler superBuilderHandler) {
    super(configDiscovery, allArgsConstructorProcessor, superBuilderHandler, SuperBuilder.class);
    this.superBuilderHandler = superBuilderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_SUPER_BUILDER_ENABLED);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String builderClassName = superBuilderHandler.getBuilderClassName(psiClass);
    final PsiClass builderBaseClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderBaseClass) {
      superBuilderHandler.createBuilderBasedConstructor(psiClass, builderBaseClass, psiAnnotation)
        .ifPresent(target::add);

      // skip generation of builder methods, if class is abstract
      if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final String builderImplClassName = superBuilderHandler.getBuilderImplClassName(psiClass);
        final PsiClass builderImplClass = psiClass.findInnerClassByName(builderImplClassName, false);

        if (null != builderImplClass) {
          superBuilderHandler.createBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation)
            .ifPresent(target::add);

          superBuilderHandler.createToBuilderMethodIfNecessary(psiClass, builderBaseClass, builderImplClass, psiAnnotation)
            .ifPresent(target::add);
        }
      }
    }
  }

}

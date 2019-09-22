package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Inspect and validate @SuperBuilder lombok annotation on a class
 * Creates inner classes for a @SuperBuilder pattern
 *
 * @author Michail Plushnikov
 */
public class SuperBuilderClassProcessor extends AbstractClassProcessor {

  private final SuperBuilderHandler builderHandler;

  public SuperBuilderClassProcessor(@NotNull SuperBuilderHandler superBuilderHandler) {
    super(PsiClass.class, SuperBuilder.class);
    builderHandler = superBuilderHandler;
  }

  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_SUPER_BUILDER_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String builderClassName = builderHandler.getBuilderClassName(psiClass);

    Optional<PsiClass> builderClass = PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
    if (!builderClass.isPresent()) {
      final PsiClass createdBuilderClass = builderHandler.createBuilderBaseClass(psiClass, psiAnnotation);
      target.add(createdBuilderClass);
      builderClass = Optional.of(createdBuilderClass);
    }

    // skip generation of BuilderImpl class, if class is abstract
    if (!psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final String builderImplClassName = builderHandler.getBuilderImplClassName(psiClass);
      if (!PsiClassUtil.getInnerClassInternByName(psiClass, builderImplClassName).isPresent()) {
        target.add(builderHandler.createBuilderImplClass(psiClass, builderClass.get(), psiAnnotation));
      }
    }
  }
}

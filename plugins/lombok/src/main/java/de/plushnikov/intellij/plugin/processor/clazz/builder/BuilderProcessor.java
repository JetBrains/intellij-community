package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
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

  private static final String SINGULAR_CLASS = Singular.class.getName();
  private static final String BUILDER_DEFAULT_CLASS = Builder.Default.class.getName().replace("$", ".");

  private final BuilderHandler builderHandler;
  private final AllArgsConstructorProcessor allArgsConstructorProcessor;

  public BuilderProcessor(@NotNull ConfigDiscovery configDiscovery,
                          @NotNull AllArgsConstructorProcessor allArgsConstructorProcessor,
                          @NotNull BuilderHandler builderHandler) {
    this(configDiscovery, allArgsConstructorProcessor, builderHandler, Builder.class);
  }

  BuilderProcessor(@NotNull ConfigDiscovery configDiscovery,
                   @NotNull AllArgsConstructorProcessor allArgsConstructorProcessor,
                   @NotNull BuilderHandler builderHandler,
                   @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(configDiscovery, PsiMethod.class, supportedAnnotationClass);
    this.builderHandler = builderHandler;
    this.allArgsConstructorProcessor = allArgsConstructorProcessor;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, SINGULAR_CLASS, BUILDER_DEFAULT_CLASS);
    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    // we skip validation here, because it will be validated by other BuilderClassProcessor
    return true;//builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (PsiAnnotationSearchUtil.isNotAnnotatedWith(psiClass, AllArgsConstructor.class)) {
      // Create all args constructor only if there is no declared constructors and no lombok constructor annotations
      final Collection<PsiMethod> definedConstructors = PsiClassUtil.collectClassConstructorIntern(psiClass);
      if (definedConstructors.isEmpty()) {
        target.addAll(allArgsConstructorProcessor.createAllArgsConstructor(psiClass, PsiModifier.PACKAGE_LOCAL, psiAnnotation));
      }
    }

    final String builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, null);
    final PsiClass builderClass = psiClass.findInnerClassByName(builderClassName, false);
    if (null != builderClass) {
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

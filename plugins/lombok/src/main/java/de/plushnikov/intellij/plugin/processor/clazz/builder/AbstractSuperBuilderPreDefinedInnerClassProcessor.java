package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractSuperBuilderPreDefinedInnerClassProcessor extends AbstractClassProcessor {

  final SuperBuilderHandler builderHandler;

  AbstractSuperBuilderPreDefinedInnerClassProcessor(@NotNull ConfigDiscovery configDiscovery,
                                                    @NotNull SuperBuilderHandler builderHandler,
                                                    @NotNull Class<? extends PsiElement> supportedClass,
                                                    @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(configDiscovery, supportedClass, supportedAnnotationClass);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_SUPER_BUILDER_ENABLED);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    final Optional<PsiClass> parentClass = getSupportedParentClass(psiClass);
    final Optional<PsiAnnotation> psiAnnotation = parentClass.map(this::getSupportedAnnotation);
    if (psiAnnotation.isPresent()) {
      return processAnnotation(parentClass.get(), psiAnnotation.get(), psiClass);
    }
    return Collections.emptyList();
  }

  private Optional<PsiClass> getSupportedParentClass(PsiClass psiClass) {
    final PsiElement parentElement = psiClass.getParent();
    if (parentElement instanceof PsiClass && !(parentElement instanceof LombokLightClassBuilder)) {
      return Optional.of((PsiClass) parentElement);
    }
    return Optional.empty();
  }

  @Nullable
  private PsiAnnotation getSupportedAnnotation(@NotNull PsiClass psiParentClass) {
    return PsiAnnotationSearchUtil.findAnnotation(psiParentClass, getSupportedAnnotationClasses());
  }

  private List<? super PsiElement> processAnnotation(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass) {
    // use parent class as source!
    final String builderBaseClassName = builderHandler.getBuilderClassName(psiParentClass);
    final String builderImplClassName = builderHandler.getBuilderImplClassName(psiParentClass);

    List<? super PsiElement> result = new ArrayList<>();
    // apply only to inner BuilderClass
    if (builderBaseClassName.equals(psiClass.getName())) {
      result.addAll(generatePsiElementsOfBaseBuilderClass(psiParentClass, psiAnnotation, psiClass));
    } else if (builderImplClassName.equals(psiClass.getName())) {
      result.addAll(generatePsiElementsOfImplBuilderClass(psiParentClass, psiAnnotation, psiClass));
    }
    return result;
  }

  protected abstract Collection<? extends PsiElement> generatePsiElementsOfBaseBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass);

  protected abstract Collection<? extends PsiElement> generatePsiElementsOfImplBuilderClass(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass);

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    //do nothing
    return Collections.emptySet();
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    //do nothing
    return true;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    //do nothing
  }
}

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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    List<? super PsiElement> result = Collections.emptyList();

    final PsiElement parentElement = psiClass.getParent();
    if (parentElement instanceof PsiClass && !(parentElement instanceof LombokLightClassBuilder)) {
      result = new ArrayList<>();

      final PsiClass psiParentClass = (PsiClass) parentElement;
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParentClass, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        processAnnotation(psiParentClass, psiAnnotation, psiClass, result);
      }
    }

    return result;
  }

  private void processAnnotation(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, List<? super PsiElement> result) {
    // use parent class as source!
    final String builderBaseClassName = builderHandler.getBuilderClassName(psiParentClass);
    final String builderImplClassName = builderHandler.getBuilderImplClassName(psiParentClass);

    // apply only to inner BuilderClass
    if (builderBaseClassName.equals(psiClass.getName())) {
      result.addAll(generatePsiElementsOfBaseBuilderClass(psiParentClass, psiAnnotation, psiClass));
    } else if (builderImplClassName.equals(psiClass.getName())) {
      result.addAll(generatePsiElementsOfImplBuilderClass(psiParentClass, psiAnnotation, psiClass));
    }
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

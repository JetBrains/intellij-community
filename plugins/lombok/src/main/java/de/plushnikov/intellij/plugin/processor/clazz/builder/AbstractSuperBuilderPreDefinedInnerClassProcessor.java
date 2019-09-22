package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractSuperBuilderPreDefinedInnerClassProcessor extends AbstractClassProcessor {

  final SuperBuilderHandler builderHandler;

  AbstractSuperBuilderPreDefinedInnerClassProcessor(@NotNull SuperBuilderHandler builderHandler,
                                                    @NotNull Class<? extends PsiElement> supportedClass,
                                                    @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
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
      final PsiClass psiParentClass = parentClass.get();
      final PsiAnnotation psiBuilderAnnotation = psiAnnotation.get();
      // use parent class as source!
      if (validate(psiBuilderAnnotation, psiParentClass, ProblemEmptyBuilder.getInstance())) {
        return processAnnotation(psiParentClass, psiBuilderAnnotation, psiClass);
      }
    }
    return Collections.emptyList();
  }

  private List<? super PsiElement> processAnnotation(@NotNull PsiClass psiParentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass) {
    // use parent class as source!
    final String builderBaseClassName = builderHandler.getBuilderClassName(psiParentClass);

    List<? super PsiElement> result = new ArrayList<>();
    // apply only to inner BuilderClass
    final String psiClassName = psiClass.getName();
    if (builderBaseClassName.equals(psiClassName)) {
      result.addAll(generatePsiElementsOfBaseBuilderClass(psiParentClass, psiAnnotation, psiClass));
    } else {
      // use parent class as source!
      final String builderImplClassName = builderHandler.getBuilderImplClassName(psiParentClass);
      if (builderImplClassName.equals(psiClassName)) {
        result.addAll(generatePsiElementsOfImplBuilderClass(psiParentClass, psiAnnotation, psiClass));
      }
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
    return builderHandler.validate(psiClass, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    //do nothing
  }
}

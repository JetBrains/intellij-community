package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class AbstractBuilderPreDefinedInnerClassProcessor extends AbstractClassProcessor {

  final BuilderHandler builderHandler;

  AbstractBuilderPreDefinedInnerClassProcessor(@NotNull BuilderHandler builderHandler,
                                               @NotNull Class<? extends PsiElement> supportedClass,
                                               @NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    final Optional<PsiClass> parentClass = getSupportedParentClass(psiClass);
    final Optional<PsiAnnotation> builderAnnotation = parentClass.map(this::getSupportedAnnotation);
    if (builderAnnotation.isPresent()) {
      final PsiClass psiParentClass = parentClass.get();
      final PsiAnnotation psiBuilderAnnotation = builderAnnotation.get();
      // use parent class as source!
      if (validate(psiBuilderAnnotation, psiParentClass, ProblemEmptyBuilder.getInstance())) {
        return processAnnotation(psiParentClass, null, psiBuilderAnnotation, psiClass);
      }
    } else if (parentClass.isPresent()) {
      final PsiClass psiParentClass = parentClass.get();
      final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiParentClass);
      for (PsiMethod psiMethod : psiMethods) {
        final PsiAnnotation psiBuilderAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiMethod, getSupportedAnnotationClasses());
        if (null != psiBuilderAnnotation) {
          return processAnnotation(psiParentClass, psiMethod, psiBuilderAnnotation, psiClass);
        }
      }
    }
    return Collections.emptyList();
  }

  private List<? super PsiElement> processAnnotation(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod,
                                                     @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass) {
    // use parent class as source!
    final String builderClassName = builderHandler.getBuilderClassName(psiParentClass, psiAnnotation, psiParentMethod);

    List<? super PsiElement> result = new ArrayList<>();
    // apply only to inner BuilderClass
    if (builderClassName.equals(psiClass.getName())) {
      result.addAll(generatePsiElements(psiParentClass, psiParentMethod, psiAnnotation, psiClass));
    }
    return result;
  }

  protected abstract Collection<? extends PsiElement> generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiBuilderClass);

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

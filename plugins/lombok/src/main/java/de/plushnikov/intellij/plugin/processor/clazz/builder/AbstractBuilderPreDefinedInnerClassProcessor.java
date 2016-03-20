package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
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

public abstract class AbstractBuilderPreDefinedInnerClassProcessor extends AbstractClassProcessor {

  protected final BuilderHandler builderHandler;

  AbstractBuilderPreDefinedInnerClassProcessor(@NotNull BuilderHandler builderHandler, @NotNull Class<? extends PsiElement> supportedClass,
                                               @NotNull Class<? extends Annotation> supportedAnnotationClass,
                                               @NotNull Class<? extends Annotation>... equivalentAnnotationClasses) {
    super(supportedClass, supportedAnnotationClass, equivalentAnnotationClasses);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    List<? super PsiElement> result = Collections.emptyList();

    final PsiElement parentElement = psiClass.getParent();
    if (parentElement instanceof PsiClass && !(parentElement instanceof LombokLightClassBuilder)) {
      result = new ArrayList<PsiElement>();

      final PsiClass psiParentClass = (PsiClass) parentElement;
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParentClass, getSupportedAnnotationClasses());
      if (null == psiAnnotation) {
        final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiParentClass);
        for (PsiMethod psiMethod : psiMethods) {
          psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiMethod, getSupportedAnnotationClasses());
          if (null != psiAnnotation) {
            processMethodAnnotation(result, psiMethod, psiAnnotation, psiClass, psiParentClass);
          }
        }
      } else {
        processMethodAnnotation(result, null, psiAnnotation, psiClass, psiParentClass);
      }
    }

    return result;
  }

  private void processMethodAnnotation(List<? super PsiElement> result, PsiMethod psiParentMethod, PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, PsiClass psiParentClass) {
    final PsiType psiBuilderType = builderHandler.getBuilderType(psiParentClass, psiParentMethod);
    final String builderClassName;

    if (null == psiParentMethod) {
      builderClassName = builderHandler.getBuilderClassName(psiParentClass, psiAnnotation, psiBuilderType);
    } else {
      builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
    }

    // apply only to inner BuilderClass
    if (builderClassName.equals(psiClass.getName())) {
      generatePsiElements(psiParentClass, psiParentMethod, psiClass, psiAnnotation, result);
    }
  }

  protected abstract void generatePsiElements(@NotNull PsiClass psiParentClass, @Nullable PsiMethod psiParentMethod, @NotNull PsiClass psiBuilderClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target);

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

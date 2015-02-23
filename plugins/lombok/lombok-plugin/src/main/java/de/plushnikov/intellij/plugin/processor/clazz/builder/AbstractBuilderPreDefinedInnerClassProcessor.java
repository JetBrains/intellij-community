package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightClass;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractBuilderPreDefinedInnerClassProcessor extends AbstractClassProcessor {

  protected final BuilderHandler builderHandler = new BuilderHandler();

  public AbstractBuilderPreDefinedInnerClassProcessor(Class<? extends Annotation> supportedAnnotationClass, Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    List<? super PsiElement> result = Collections.emptyList();

    final PsiElement parentElement = psiClass.getParent();
    if (parentElement instanceof PsiClass && !(parentElement instanceof LombokLightClass)) {
      PsiMethod psiParentMethod = null;
      final PsiClass psiParentClass = (PsiClass) parentElement;
      PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiParentClass, getSupportedAnnotation());
      if (null == psiAnnotation) {
        final Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiParentClass);
        for (PsiMethod psiMethod : psiMethods) {
          psiAnnotation = PsiAnnotationUtil.findAnnotation(psiMethod, getSupportedAnnotation());
          if (null != psiAnnotation) {
            psiParentMethod = psiMethod;
            break;
          }
        }
      }

      if (null != psiAnnotation) {
        final PsiType psiBuilderType = builderHandler.getBuilderType(psiParentClass, psiParentMethod);
        final String builderClassName;

        if (null == psiParentMethod) {
          builderClassName = builderHandler.getBuilderClassName(psiParentClass, psiAnnotation, psiBuilderType);
        } else {
          builderClassName = builderHandler.getBuilderClassName(psiClass, psiAnnotation, psiBuilderType);
        }

        // apply only to inner BuilderClass
        if (builderClassName.equals(psiClass.getName())) {
          result = new ArrayList<PsiElement>();
          generatePsiElements(psiParentClass, psiParentMethod, psiClass, psiAnnotation, result);
        }
      }
    }

    return result;
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

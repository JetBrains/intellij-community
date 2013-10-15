package de.plushnikov.intellij.lombok.processor.method;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemNewBuilder;
import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Base lombok processor class for method annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokMethodProcessor extends AbstractLombokProcessor implements LombokMethodProcessor {

  protected AbstractLombokMethodProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    List<? super PsiElement> result = new ArrayList<PsiElement>();
    for (PsiMethod psiMethod : PsiClassUtil.collectClassMethodsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(psiMethod, Collections.singleton(getSupportedAnnotation()), true);
      if (null != psiAnnotation) {
        process(psiMethod, psiAnnotation, result);
      }
    }
    return result;
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiAnnotation, PsiMethod.class);
    if (null != psiMethod) {
      result = new ArrayList<LombokProblem>(1);
      validate(psiAnnotation, psiMethod, new ProblemNewBuilder(result));
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder);

  public final void process(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    if (validate(psiAnnotation, psiMethod, ProblemEmptyBuilder.getInstance())) {
      processIntern(psiMethod, psiAnnotation, target);
    }
  }

  protected abstract void processIntern(PsiMethod psiMethod, PsiAnnotation psiAnnotation, List<? super PsiElement> target);
}

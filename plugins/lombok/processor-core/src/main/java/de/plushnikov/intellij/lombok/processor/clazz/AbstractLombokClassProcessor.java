package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemNewBuilder;
import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import de.plushnikov.intellij.lombok.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base lombok processor class for class annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokClassProcessor extends AbstractLombokProcessor implements LombokClassProcessor {

  protected AbstractLombokClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();

    PsiClass psiClass = PsiElementUtil.getParentOfType(psiAnnotation, PsiClass.class);
    if (null != psiClass) {
      result = new ArrayList<LombokProblem>(1);
      validate(psiAnnotation, psiClass, new ProblemNewBuilder(result));
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder);

  public final <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (validate(psiAnnotation, psiClass, new ProblemEmptyBuilder())) {
      processIntern(psiClass, psiAnnotation, target);
    }
  }

  protected abstract <Psi extends PsiElement> void processIntern(PsiClass psiClass, PsiAnnotation psiAnnotation, List<Psi> target);
}

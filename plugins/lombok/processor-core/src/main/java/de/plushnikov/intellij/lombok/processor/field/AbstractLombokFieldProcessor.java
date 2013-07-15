package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.lombok.problem.LombokProblem;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.lombok.problem.ProblemNewBuilder;
import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Base lombok processor class for field annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokFieldProcessor extends AbstractLombokProcessor implements LombokFieldProcessor {

  protected AbstractLombokFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<?> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();

    PsiField psiField = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class);
    if (null != psiField) {
      result = new ArrayList<LombokProblem>(1);
      validate(psiAnnotation, psiField, new ProblemNewBuilder(result));
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder);

  public final <Psi extends PsiElement> void process(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (validate(psiAnnotation, psiField, ProblemEmptyBuilder.getInstance())) {
      processIntern(psiField, psiAnnotation, target);
    }
  }

  protected abstract <Psi extends PsiElement> void processIntern(PsiField psiField, PsiAnnotation psiAnnotation, List<Psi> target);

  protected void copyAnnotations(final PsiField fromPsiElement, final PsiModifierList toModifierList, final Pattern... patterns) {
    final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(fromPsiElement, patterns);
    for (String annotationFQN : annotationsToCopy) {
      toModifierList.addAnnotation(annotationFQN);
    }
  }

}

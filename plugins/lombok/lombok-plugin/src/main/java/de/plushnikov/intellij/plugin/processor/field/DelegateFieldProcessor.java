package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateFieldProcessor extends AbstractFieldProcessor {
  private final DelegateHandler handler;

  public DelegateFieldProcessor() {
    this(Delegate.class);
  }

  protected DelegateFieldProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass) {
    super(supportedAnnotationClass, PsiMethod.class);
    handler = new DelegateHandler();
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    final PsiType psiFieldType = psiField.getType();
    return handler.validate(psiField, psiFieldType, psiAnnotation, builder);
  }

  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    handler.generateElements(psiField, psiField.getType(), psiAnnotation, target);
  }
}

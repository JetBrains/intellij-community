package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import lombok.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateFieldProcessor extends AbstractFieldProcessor {
  private final DelegateHandler delegateHandler;

  @SuppressWarnings({"deprecation"})
  public DelegateFieldProcessor(@NotNull DelegateHandler delegateHandler) {
    super(PsiMethod.class, Delegate.class, lombok.experimental.Delegate.class);
    this.delegateHandler = delegateHandler;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    final PsiType psiFieldType = psiField.getType();
    return delegateHandler.validate(psiField, psiFieldType, psiAnnotation, builder);
  }

  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    delegateHandler.generateElements(psiField, psiField.getType(), psiAnnotation, target);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ;
  }
}

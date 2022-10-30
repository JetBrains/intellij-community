package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @Delegate lombok annotation on a field
 * Creates delegation methods for this field
 *
 * @author Plushnikov Michail
 */
public class DelegateFieldProcessor extends AbstractFieldProcessor {

  public DelegateFieldProcessor() {
    super(PsiMethod.class, LombokClassNames.DELEGATE, LombokClassNames.EXPERIMENTAL_DELEGATE);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemSink builder) {
    final PsiType psiFieldType = psiField.getType();
    return DelegateHandler.validate(psiField, psiFieldType, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    DelegateHandler.generateElements(psiField, psiField.getType(), psiAnnotation, target);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ;
  }
}

package de.plushnikov.intellij.plugin.processor.field;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
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

  private DelegateHandler getDelegateHandler() {
    return ApplicationManager.getApplication().getService(DelegateHandler.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    final PsiType psiFieldType = psiField.getType();
    return getDelegateHandler().validate(psiField, psiFieldType, psiAnnotation, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    getDelegateHandler().generateElements(psiField, psiField.getType(), psiAnnotation, target);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ;
  }
}

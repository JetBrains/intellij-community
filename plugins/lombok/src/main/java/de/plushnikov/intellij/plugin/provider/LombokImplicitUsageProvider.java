package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierListOwner;
import de.plushnikov.intellij.plugin.processor.LombokProcessorManager;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AbstractFieldProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Provides implicit usages of lombok fields
 */
public final class LombokImplicitUsageProvider implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    return checkUsage(element, EnumSet.of(LombokPsiElementUsage.READ, LombokPsiElementUsage.WRITE, LombokPsiElementUsage.READ_WRITE));
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return checkUsage(element, EnumSet.of(LombokPsiElementUsage.READ, LombokPsiElementUsage.READ_WRITE));
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    return checkUsage(element, EnumSet.of(LombokPsiElementUsage.WRITE, LombokPsiElementUsage.READ_WRITE));
  }

  private static boolean checkUsage(@NotNull PsiElement element, EnumSet<LombokPsiElementUsage> elementUsages) {
    if (element instanceof PsiField psiField) {
      if (isUsedByLombokAnnotations(psiField, psiField, AbstractFieldProcessor.class::isInstance, elementUsages) ||
          isUsedByLombokAnnotations(psiField, psiField.getContainingClass(), AbstractClassProcessor.class::isInstance, elementUsages)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUsedByLombokAnnotations(@NotNull PsiField psiField,
                                                   @Nullable PsiModifierListOwner modifierListOwner,
                                                   Predicate<Processor> kindOfProcessor,
                                                   EnumSet<LombokPsiElementUsage> elementUsages) {
    if (null != modifierListOwner) {
      for (PsiAnnotation psiAnnotation : modifierListOwner.getAnnotations()) {
        for (Processor processor : LombokProcessorManager.getProcessors(psiAnnotation)) {
          if(kindOfProcessor.test(processor)) {
            final LombokPsiElementUsage psiElementUsage = processor.checkFieldUsage(psiField, psiAnnotation);
            if (elementUsages.contains(psiElementUsage)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}

package com.jetbrains.python.codeInsight;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PyStdReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registerClassAttributeReference(registrar, PyNames.ALL, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                   @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderAllReference((PyStringLiteralExpression)element)};
      }
    });

    registerClassAttributeReference(registrar, PyNames.SLOTS, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                   @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderSlotsReference((PyStringLiteralExpression)element)};
      }
    });
  }

  private static void registerClassAttributeReference(PsiReferenceRegistrar registrar,
                                                      final String name,
                                                      final PsiReferenceProvider provider) {
    registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class).withParent(
                                          psiElement(PySequenceExpression.class).withParent(
                                            psiElement(PyAssignmentStatement.class).withFirstChild(
                                              psiElement(PyTargetExpression.class).withName(name)))), provider);
   }
}

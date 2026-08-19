// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

final class ContributorHelper {
  private ContributorHelper() {
  }

  static boolean contributesGetter(AbstractClassProcessor processor, @NotNull PsiField psiField) {
    return contributes(processor, psiField, containingClass -> isShadowedByGetterProcessor(processor, containingClass)) &&
           GetterProcessor.shouldCreateGetter(psiField);
  }

  private static boolean isShadowedByGetterProcessor(AbstractClassProcessor processor, PsiClass containingClass) {
    return !(processor instanceof GetterProcessor) && PsiAnnotationSearchUtil.isAnnotatedWith(containingClass, LombokClassNames.GETTER);
  }

  static boolean contributesSetter(AbstractClassProcessor processor, @NotNull PsiField psiField) {
    return contributes(processor, psiField, containingClass -> isShadowedBySetterProcessor(processor, containingClass)) &&
           SetterProcessor.shouldCreateSetter(psiField);
  }

  private static boolean isShadowedBySetterProcessor(AbstractClassProcessor processor, PsiClass containingClass) {
    return !(processor instanceof SetterProcessor) && PsiAnnotationSearchUtil.isAnnotatedWith(containingClass, LombokClassNames.SETTER);
  }

  private static boolean contributes(AbstractClassProcessor processor,
                                     @NotNull PsiField psiField,
                                     @NotNull Predicate<? super PsiClass> isShadowed) {
    PsiClass containingClass = psiField.getContainingClass();
    if (containingClass == null) return false;
    if (isShadowed.test(containingClass)) return false;
    PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(containingClass, processor.getSupportedAnnotationClasses());
    if (psiAnnotation == null) return false;
    return processor.validate(psiAnnotation, containingClass, new ProblemProcessingSink());
  }
}

package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public interface Processor {
  @NotNull
  String @NotNull[] getSupportedAnnotationClasses();

  @NotNull
  Class<? extends PsiElement> getSupportedClass();

  @NotNull
  Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation);

  default boolean notNameHintIsEqualToSupportedAnnotation(@Nullable String nameHint) {
    return null == nameHint || (!"lombok".equals(nameHint) && Arrays.stream(getSupportedAnnotationClasses())
      .map(StringUtil::getShortName).noneMatch(nameHint::equals));
  }

  @NotNull
  default List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    return process(psiClass, null);
  }

  @NotNull
  default List<? super PsiElement> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    return Collections.emptyList();
  }

  LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation);
}

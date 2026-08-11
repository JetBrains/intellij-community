package de.plushnikov.intellij.plugin.processor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/// Processes Lombok annotations for IDE code insight.
///
/// A processor declares:
/// - fully-qualified annotation names it supports
/// - the PSI element type it can contribute
///
/// Implementations validate matching annotations, generate synthetic PSI elements for the containing class, and report field
/// usage for Lombok-generated elements.
public interface Processor {
  /// Returns fully-qualified Lombok annotation names handled by this processor.
  ///
  /// The array may include equivalent annotations that share the same processing logic.
  ///
  /// @return supported annotation fully-qualified names
  @NotNull
  String @NotNull[] getSupportedAnnotationClasses();

  /// Returns the PSI element type contributed by this processor.
  ///
  /// The processor manager uses this value to route augmentation requests by requested PSI type.
  ///
  /// @return generated PSI element class
  @NotNull
  Class<? extends PsiElement> getSupportedClass();

  /// Checks whether this processor handles the annotation with the given fully-qualified name.
  ///
  /// @param annotationFQN annotation fully-qualified name to check
  /// @return `true` when `annotationFQN` is one of the supported annotation names
  default boolean isSupportedAnnotationFQN(String annotationFQN) {
    return ContainerUtil.exists(getSupportedAnnotationClasses(), annotationFQN::equals);
  }

  /// Checks whether this processor can contribute the requested PSI element type.
  ///
  /// @param someClass PSI element type requested by augmentation
  /// @return `true` when `someClass` equals the supported PSI element type
  default boolean isSupportedClass(Class<? extends PsiElement> someClass) {
    return getSupportedClass().equals(someClass);
  }

  /// Validates a supported Lombok annotation and returns problems for inspection reporting.
  ///
  /// @param psiAnnotation annotation to validate
  /// @return validation problems found for the annotation or an empty collection when the annotation is valid
  @NotNull
  Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation);

  /// Generates all synthetic PSI elements this processor contributes for `psiClass`.
  ///
  /// @param psiClass class whose Lombok annotations should be processed
  /// @return generated PSI elements or an empty list when nothing is generated
  default @NotNull List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    return process(psiClass, null);
  }

  /// Generates synthetic PSI elements for `psiClass`, optionally limited by the requested element name.
  ///
  /// `nameHint` is supplied by augment callers when only elements with a particular name are needed; implementations may use it to
  /// skip unrelated generation.
  ///
  /// @param psiClass class whose Lombok annotations should be processed
  /// @param nameHint optional generated element name requested by the caller, or `null` to generate all matching elements
  /// @return generated PSI elements or an empty list when nothing is generated
  default @NotNull List<? super PsiElement> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    return Collections.emptyList();
  }

  /// Classifies the implicit usage that a supported Lombok annotation introduces for a source field.
  ///
  /// @param psiField field whose implicit usage is being checked
  /// @param psiAnnotation supported annotation that may generate usage of `psiField`
  /// @return usage kind introduced for `psiField` by `psiAnnotation`
  LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation);
}

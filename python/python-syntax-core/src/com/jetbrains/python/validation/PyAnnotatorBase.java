package com.jetbrains.python.validation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ast.PyAstFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.SYMBOL_TYPE_SEVERITY;

@ApiStatus.Experimental
public interface PyAnnotatorBase {
  @ApiStatus.Internal
  static void runAnnotators(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder, PyAnnotatorBase[] annotators) {
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    for (PyAnnotatorBase annotator : annotators) {
      if (file instanceof PyAstFile && !((PyAstFile)file).isAcceptedFor(annotator.getClass())) continue;
      annotator.annotateElement(psiElement, holder);
    }
  }

  @ApiStatus.Internal
  boolean isTestMode();

  AnnotationHolder getHolder();

  void annotateElement(final PsiElement psiElement, final AnnotationHolder holder);

  @ApiStatus.Internal
  default void markError(@NotNull PsiElement element, @NotNull @InspectionMessage String message) {
    getHolder().newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
  }

  @ApiStatus.Internal
  default void addHighlightingAnnotation(@NotNull PsiElement target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target, key, SYMBOL_TYPE_SEVERITY);
  }

  @ApiStatus.Internal
  default void addHighlightingAnnotation(@NotNull PsiElement target,
                                         @NotNull TextAttributesKey key,
                                         @NotNull HighlightSeverity severity) {
    final String message = isTestMode() ? key.getExternalName() : null;
    // CodeInsightTestFixture#testHighlighting doesn't consider annotations with severity level < INFO
    final HighlightSeverity actualSeverity =
      isTestMode() && severity.myVal < HighlightSeverity.INFORMATION.myVal ? HighlightSeverity.INFORMATION : severity;
    (message == null ? getHolder().newSilentAnnotation(actualSeverity) : getHolder().newAnnotation(actualSeverity, message))
      .range(target).textAttributes(key).create();
  }

  @ApiStatus.Internal
  default void addHighlightingAnnotation(@NotNull ASTNode target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target.getPsi(), key);
  }
}

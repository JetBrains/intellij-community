package com.jetbrains.python.validation;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.daemon.impl.HighlightInfoType.SYMBOL_TYPE_SEVERITY;

@ApiStatus.Internal
public final class PyAnnotationHolder {
  private final boolean myTestMode = ApplicationManager.getApplication().isUnitTestMode();
  private final @NotNull AnnotationHolder myHolder;

  public PyAnnotationHolder(@NotNull AnnotationHolder holder) { myHolder = holder; }

  @Contract(pure = true)
  public @NotNull AnnotationBuilder newAnnotation(@NotNull HighlightSeverity severity, @NotNull @InspectionMessage String message) {
    return myHolder.newAnnotation(severity, message);
  }

  @Contract(pure = true)
  public @NotNull AnnotationBuilder newSilentAnnotation(@NotNull HighlightSeverity severity) {
    return myHolder.newSilentAnnotation(severity);
  }

  public void markError(@NotNull PsiElement element, @NotNull @InspectionMessage String message) {
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
  }

  public void addHighlightingAnnotation(@NotNull PsiElement target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target, key, SYMBOL_TYPE_SEVERITY);
  }

  public void addHighlightingAnnotation(@NotNull PsiElement target,
                                        @NotNull TextAttributesKey key,
                                        @NotNull HighlightSeverity severity) {
    final String message = myTestMode ? key.getExternalName() : null;
    // CodeInsightTestFixture#testHighlighting doesn't consider annotations with severity level < INFO
    final HighlightSeverity actualSeverity =
      myTestMode && severity.myVal < HighlightSeverity.INFORMATION.myVal ? HighlightSeverity.INFORMATION : severity;
    (message == null ? myHolder.newSilentAnnotation(actualSeverity) : myHolder.newAnnotation(actualSeverity, message))
      .range(target).textAttributes(key).create();
  }

  public void addHighlightingAnnotation(@NotNull ASTNode target, @NotNull TextAttributesKey key) {
    addHighlightingAnnotation(target.getPsi(), key);
  }

  public @NotNull AnnotationHolder getOriginalHolder() {
    return myHolder;
  }
}

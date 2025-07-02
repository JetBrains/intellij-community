package com.jetbrains.python.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ast.PyAstFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class PyAnnotatorBase implements Annotator {
  @Override
  public final void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    if (!(file instanceof PyAstFile pyFile && !pyFile.isAcceptedFor(getClass()))) {
      annotate(element, new PyAnnotationHolder(holder));
    }
  }

  protected abstract void annotate(@NotNull PsiElement element, @NotNull PyAnnotationHolder holder);
}

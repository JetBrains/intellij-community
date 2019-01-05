package com.intellij.bash;

import com.intellij.bash.psi.BashString;
import com.intellij.bash.psi.BashVisitor;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.bash.BashSyntaxHighlighter.STRING;

public class BashAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    element.accept(new BashVisitor() {
      @Override
      public void visitString(@NotNull BashString o) {
        holder.createInfoAnnotation(TextRange.from(o.getTextOffset(), o.getTextLength()), null).setTextAttributes(STRING);
      }
    });
  }
}

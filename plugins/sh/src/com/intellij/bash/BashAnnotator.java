package com.intellij.bash;

import com.intellij.bash.psi.BashString;
import com.intellij.bash.psi.BashVariable;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.bash.BashSyntaxHighlighter.STRING;
import static com.intellij.bash.BashSyntaxHighlighter.VAR_USE;

public class BashAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement o, @NotNull AnnotationHolder holder) {
    if (o instanceof BashString) {
      holder.createInfoAnnotation(o, null).setTextAttributes(STRING);
      highlightVariables(o, holder);
    }
  }

  private void highlightVariables(@NotNull PsiElement container, @NotNull AnnotationHolder holder) {
    new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof BashVariable) {
          holder.createInfoAnnotation(element, null).setTextAttributes(VAR_USE);
        }
        super.visitElement(element);
      }
    }.visitElement(container);
  }
}

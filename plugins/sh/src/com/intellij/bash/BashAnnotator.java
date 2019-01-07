package com.intellij.bash;

import com.intellij.bash.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.bash.BashSyntaxHighlighter.*;

public class BashAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement o, @NotNull AnnotationHolder holder) {
    if (o instanceof BashString) {
      mark(o, holder, STRING);
      highlightVariables(o, holder);
    }
    else if (o instanceof BashHeredoc) {
      annotateHeredoc((BashHeredoc) o, holder);
    }
    else if (o instanceof BashGenericCommandDirective) {
      mark(o, holder, EXTERNAL_COMMAND);
    }

    ASTNode node = o.getNode();
    IElementType elementType = node == null ? null : node.getElementType();
    if (elementType == BashTypes.WORD && o.getParent() instanceof BashSimpleCommandElement) {
      holder.createInfoAnnotation(node, null).setEnforcedTextAttributes(TextAttributes.ERASE_MARKER);
    }
  }

  private void annotateHeredoc(@NotNull BashHeredoc o, @NotNull AnnotationHolder holder) {
    mark(o, holder, HERE_DOC);
    mark(o.getHeredocMarkerStart(), holder, HERE_DOC_START);
    mark(o.getHeredocMarkerEnd(), holder, HERE_DOC_END);

    highlightVariables(o, holder);
  }

  private void highlightVariables(@NotNull PsiElement container, @NotNull AnnotationHolder holder) {
    new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof BashVariable) {
          mark(element, holder, VAR_USE);
        }
        super.visitElement(element);
      }
    }.visitElement(container);
  }

  private static void mark(@Nullable PsiElement o, @NotNull AnnotationHolder holder, @NotNull TextAttributesKey key) {
    if (o != null) holder.createInfoAnnotation(o, null).setTextAttributes(key);
  }
}

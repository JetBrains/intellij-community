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

import static com.intellij.bash.highlighter.BashHighlighterColors.*;

public class BashAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement o, @NotNull AnnotationHolder holder) {
    if (o instanceof BashString) {
      mark(o, holder, STRING);
      highlightVariables(o, holder);
    }
    // todo comment in case of poor performance because of the issue with EditorGutterComponentImpl#updateSize()
    else if (o instanceof BashGenericCommandDirective) {
      mark(o, holder, GENERIC_COMMAND);
    }
    else if (o instanceof BashFunctionDefinition) {
      PsiElement word = ((BashFunctionDefinition) o).getWord();
      mark(word, holder, FUNCTION_DECLARATION);
    }
    else if (o instanceof BashAssignmentCommand) {
      PsiElement literal = ((BashAssignmentCommand) o).getLiteral();
      mark(literal, holder, VARIABLE_DECLARATION);
    }
    else if (o instanceof BashSubshellCommand) {
      BashSubshellCommand subshellCommand = (BashSubshellCommand) o;
      mark(subshellCommand.getLeftParen(), holder, SUBSHELL_COMMAND);
      mark(subshellCommand.getRightParen(), holder, SUBSHELL_COMMAND);
    }
    ASTNode node = o.getNode();
    IElementType elementType = node == null ? null : node.getElementType();
    if (elementType == BashTypes.WORD && o.getParent() instanceof BashSimpleCommandElement) {
      holder.createInfoAnnotation(node, null).setEnforcedTextAttributes(TextAttributes.ERASE_MARKER);
    }
  }

  private static void highlightVariables(@NotNull PsiElement container, @NotNull AnnotationHolder holder) {
    new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof BashVariable) {
          mark(element, holder, VARIABLE);
        }
        super.visitElement(element);
      }
    }.visitElement(container);
  }

  private static void mark(@Nullable PsiElement o, @NotNull AnnotationHolder holder, @NotNull TextAttributesKey key) {
    if (o != null) holder.createInfoAnnotation(o, null).setTextAttributes(key);
  }
}

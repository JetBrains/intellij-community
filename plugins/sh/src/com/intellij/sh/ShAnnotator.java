package com.intellij.sh;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.highlighter.ShHighlighterColors.*;

public class ShAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement o, @NotNull AnnotationHolder holder) {
    if (o instanceof ShString) {
      mark(o, holder, STRING);
      highlightVariables(o, holder);
    }
    // todo comment in case of poor performance because of the issue with EditorGutterComponentImpl#updateSize()
    else if (o instanceof ShGenericCommandDirective) {
      mark(o, holder, GENERIC_COMMAND);
    }
    else if (o instanceof ShFunctionDefinition) {
      PsiElement word = ((ShFunctionDefinition) o).getWord();
      mark(word, holder, FUNCTION_DECLARATION);
    }
    else if (o instanceof ShAssignmentCommand) {
      PsiElement literal = ((ShAssignmentCommand) o).getLiteral();
      mark(literal, holder, VARIABLE_DECLARATION);
    }
    else if (o instanceof ShShellParameterExpansion) {
      ASTNode[] children = o.getNode().getChildren(TokenSet.create(ShTokenTypes.PARAMETER_EXPANSION_BODY));
      for (ASTNode node : children) {
        mark(node.getPsi(), holder, COMPOSED_VARIABLE);
      }
    }
    else if (o instanceof ShSubshellCommand) {
      ShSubshellCommand subshellCommand = (ShSubshellCommand) o;
      mark(subshellCommand.getLeftParen(), holder, SUBSHELL_COMMAND);
      mark(subshellCommand.getRightParen(), holder, SUBSHELL_COMMAND);
    }
    ASTNode node = o.getNode();
    IElementType elementType = node == null ? null : node.getElementType();
    if (elementType == ShTypes.WORD && o.getParent() instanceof ShSimpleCommandElement) {
      holder.createInfoAnnotation(node, null).setEnforcedTextAttributes(TextAttributes.ERASE_MARKER);
    }
  }

  private static void highlightVariables(@NotNull PsiElement container, @NotNull AnnotationHolder holder) {
    new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof ShVariable) {
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

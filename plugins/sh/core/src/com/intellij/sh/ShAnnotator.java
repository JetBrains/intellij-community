// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.sh.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.highlighter.ShHighlighterColors.*;

final class ShAnnotator implements Annotator, DumbAware {
  @Override
  public void annotate(@NotNull PsiElement o, @NotNull AnnotationHolder holder) {
    // todo comment in case of poor performance because of the issue with EditorGutterComponentImpl#updateSize()
    if (o instanceof ShGenericCommandDirective) {
      mark(o, holder, GENERIC_COMMAND);
    }
    else if (o instanceof ShAssignmentCommand) {
      PsiElement literal = ((ShAssignmentCommand)o).getLiteral();
      mark(literal, holder, VARIABLE_DECLARATION);
    }
    else if (o instanceof ShShellParameterExpansion) {
      ASTNode[] children = o.getNode().getChildren(TokenSet.create(ShTypes.PARAM_SEPARATOR));
      for (ASTNode node : children) {
        mark(node.getPsi(), holder, COMPOSED_VARIABLE);
      }
    }
    else if (o instanceof ShSubshellCommand subshellCommand) {
      mark(subshellCommand.getLeftParen(), holder, SUBSHELL_COMMAND);
      mark(subshellCommand.getRightParen(), holder, SUBSHELL_COMMAND);
    }
    ASTNode node = o.getNode();
    IElementType elementType = node == null ? null : node.getElementType();
    if (elementType == ShTypes.WORD) {
      PsiElement parent = o.getParent();
      if (parent instanceof ShSimpleCommandElement) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).enforcedTextAttributes(TextAttributes.ERASE_MARKER).create();
      }
      if (parent instanceof ShFunctionDefinition) {
        mark(o, holder, FUNCTION_DECLARATION);
      }
    }
  }

  private static void mark(@Nullable PsiElement o, @NotNull AnnotationHolder holder, @NotNull TextAttributesKey key) {
    if (o != null) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(o).textAttributes(key).create();
    }
  }
}

package com.jetbrains.typoscript.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.typoscript.lang.psi.CodeBlock;
import com.jetbrains.typoscript.lang.psi.MultilineValueAssignment;
import com.jetbrains.typoscript.lang.psi.TypoScriptFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author lene
 *         Date: 18.04.12
 */
public class TypoScriptFoldingBuilder implements FoldingBuilder, DumbAware {
  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    final PsiElement element = node.getPsi();
    if (!(element instanceof TypoScriptFile)) {
      return FoldingDescriptor.EMPTY;
    }
    final TypoScriptFile file = (TypoScriptFile)element;
    final PsiElement[] children = file.getChildren();
    Collection<FoldingDescriptor> result = new LinkedList<FoldingDescriptor>();
    for (PsiElement child : children) {
      if (child != null && (child instanceof CodeBlock || child instanceof MultilineValueAssignment || child instanceof PsiComment)) {
        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        addFoldingDescriptors(descriptors, child, document);
        result.addAll(descriptors);
      }
    }
    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private static void addFoldingDescriptors(final List<FoldingDescriptor> descriptors,
                                            final PsiElement element,
                                            @NotNull Document document) {

    TextRange elementRange = element.getTextRange();
    final int start = elementRange.getStartOffset();
    final int end = elementRange.getEndOffset();

    if (start + 1 < end) {
      TextRange range = null;
      ASTNode astNode = element.getNode();
      if (element instanceof CodeBlock) {
        range =
          buildRangeForBraces(range, astNode, TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_BEGIN, TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_END);
        addFoldingDescriptorsFromChildren(descriptors, element, document);
      }
      else if (element instanceof MultilineValueAssignment) {
        range = buildRangeForBraces(range, astNode, TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_BEGIN,
                                    TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_END);
      }
      else if (element instanceof PsiComment && ((PsiComment)element).getTokenType() == TypoScriptTokenTypes.C_STYLE_COMMENT) {
        int startIndex = astNode.getText().indexOf("/*");
        int endIndex = astNode.getText().lastIndexOf("*/");
        if (startIndex != -1 && startIndex + "/*".length() < endIndex) {
          int startOffset = element.getTextRange().getStartOffset();
          range = buildRange(range, startOffset + startIndex + "/*".length(), startOffset + endIndex);
        }
      }

      if (range != null) {
        descriptors.add(new FoldingDescriptor(astNode, range));
      }
    }
  }

  private static TextRange buildRangeForBraces(TextRange range,
                                               @NotNull ASTNode astNode,
                                               IElementType leftBraceType,
                                               IElementType rightBraceType) {
    ASTNode lBrace = astNode.findChildByType(leftBraceType);
    ASTNode rBrace = astNode.findChildByType(rightBraceType);
    if (lBrace != null && rBrace != null) {
      range = buildRange(range, lBrace.getStartOffset() + 1, rBrace.getStartOffset());
    }
    return range;
  }

  private static TextRange buildRange(TextRange range, int leftOffset, int rightOffset) {
    if (leftOffset + 1 < rightOffset) {
      range = new TextRange(leftOffset, rightOffset);
    }
    return range;
  }

  private static void addFoldingDescriptorsFromChildren(List<FoldingDescriptor> descriptors,
                                                        PsiElement element,
                                                        @NotNull Document document) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof CodeBlock || child instanceof MultilineValueAssignment || child instanceof PsiComment) {
        addFoldingDescriptors(descriptors, child, document);
      }
    }
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    return "...";
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}

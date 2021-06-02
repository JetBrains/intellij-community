// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.ShBlock;
import com.intellij.sh.psi.ShDoBlock;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShHeredoc;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShFoldingBuilder extends CustomFoldingBuilder {
  private static final String DOT_DOT_DOT = "...";
  private static final String BRACE_DOTS = "{...}";
  private static final @NlsSafe String DO_DOTS_DONE = "do...done";

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    if (!(root instanceof ShFile)) return;
    Collection<PsiElement> psiElements = PsiTreeUtil.findChildrenOfAnyType(root, ShBlock.class, ShDoBlock.class, ShHeredoc.class,
                                                                           PsiComment.class);
    foldHeredoc(descriptors, psiElements, document);
    foldComment(descriptors, psiElements, document);
    foldBlock(descriptors, psiElements);
  }

  private static void foldHeredoc(@NotNull List<FoldingDescriptor> descriptors, @NotNull Collection<PsiElement> psiElements,
                                  @NotNull Document document) {
    psiElements.forEach(element -> {
      if (element instanceof ShHeredoc) {
        PsiElement heredocContent = ((ShHeredoc)element).getHeredocContent();
        if (heredocContent == null) return;
        TextRange textRange = heredocContent.getTextRange();
        int lineNumber = document.getLineNumber(textRange.getEndOffset());
        int endOffset = document.getLineEndOffset(lineNumber - 1);
        if (textRange.getStartOffset() >= endOffset) return;
        descriptors.add(new FoldingDescriptor(element.getNode(), TextRange.create(textRange.getStartOffset(), endOffset), null,
                                              getHeredocPlaceholder(heredocContent, document)));
      }
    });
  }

  private static void foldComment(@NotNull List<FoldingDescriptor> descriptors, @NotNull Collection<PsiElement> psiElements,
                                  @NotNull Document document) {
    Set<PsiElement> handledComments = new HashSet<>();
    psiElements.forEach(element -> {
      if (element instanceof PsiComment && ((PsiComment)element).getTokenType() != ShTypes.SHEBANG && !handledComments.contains(element)) {
        PsiElement lastComment = element;
        PsiElement currentElement = element;
        handledComments.add(element);
        while (currentElement.getNextSibling() != null) {
          PsiElement nextSibling = currentElement.getNextSibling();
          IElementType elementType = nextSibling.getNode().getElementType();
          if (elementType != ShTypes.LINEFEED && elementType != ShTokenTypes.WHITESPACE && !(nextSibling instanceof PsiComment)) break;
          if (nextSibling instanceof PsiComment){
            handledComments.add(nextSibling);
            lastComment = nextSibling;
          }
          currentElement = nextSibling;
        }
        if (element == lastComment) return;
        int startOffset = element.getTextRange().getStartOffset();
        int endOffset = lastComment.getTextRange().getEndOffset();
        descriptors.add(new FoldingDescriptor(element.getContainingFile().getNode(), TextRange.create(startOffset, endOffset), null,
                                              getCommentPlaceholder(element, document)));
      }
    });
  }

  private static void foldBlock(@NotNull List<FoldingDescriptor> descriptors, @NotNull Collection<PsiElement> psiElements) {
    psiElements.forEach(element -> {
      if (element instanceof ShBlock || element instanceof ShDoBlock) descriptors.add(new FoldingDescriptor(element, element.getTextRange()));
    });
  }

  @Override
  protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    IElementType elementType = node.getElementType();
    if (elementType == ShTypes.BLOCK) return BRACE_DOTS;
    if (elementType == ShTypes.DO_BLOCK) return DO_DOTS_DONE;
    return DOT_DOT_DOT;
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }

  private static String getHeredocPlaceholder(@NotNull PsiElement heredocContent, @NotNull Document document) {
    TextRange textRange = heredocContent.getTextRange();
    int lineNumber = document.getLineNumber(textRange.getStartOffset());
    int startOffset = document.getLineStartOffset(lineNumber);
    int endOffset = document.getLineEndOffset(lineNumber);
    return document.getText(TextRange.create(startOffset, endOffset)).trim() + "...";
  }

  private static String getCommentPlaceholder(@NotNull PsiElement comment, @NotNull Document document) {
    TextRange textRange = comment.getTextRange();
    return document.getText(TextRange.create(textRange.getStartOffset() + 1, textRange.getEndOffset())).trim() + "...";
  }
}
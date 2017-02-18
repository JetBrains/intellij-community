/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.folding;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncAnnotation;
import org.intellij.plugins.relaxNG.compact.psi.RncName;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 10.08.2007
 */
public class RncFoldingBuilder implements FoldingBuilder {
  @Override
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {

    final ArrayList<FoldingDescriptor> regions = new ArrayList<>();
    process(node, document, regions);

    return regions.size() > 0
            ? regions.toArray(new FoldingDescriptor[regions.size()])
            : FoldingDescriptor.EMPTY;
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == RncTokenTypes.LBRACE) {
      return "{ ... }";
    } else if (isCommentLike(type)) {
      return "# ...";
    } else if (isAnnotation(type)) {
      final RncName element = ((RncAnnotation)node.getPsi()).getNameElement();
      if (element != null) {
        final ASTNode n = element.getNode();
        assert n != null;
        return EscapeUtil.unescapeText(n) + " [ ... ]";
      }
      return "[ ... ]";
    } else {
      return "...";
    }
  }

  private static boolean isAnnotation(IElementType type) {
    return RncElementTypes.ANNOTATION == type || RncElementTypes.ANNOTATION_ELEMENT == type || RncElementTypes.FORWARD_ANNOTATION == type;
  }

  private static boolean isCommentLike(IElementType type) {
    return RncTokenTypes.COMMENTS.contains(type) || RncTokenTypes.DOC_TOKENS.contains(type);
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return isCommentLike(node.getElementType()) && CodeFoldingSettings.getInstance().COLLAPSE_DOC_COMMENTS;
  }

  private static void process(@Nullable ASTNode node, Document document, ArrayList<FoldingDescriptor> regions) {
    if (node == null) {
      return;
    }

    final ASTNode[] braces = node.getChildren(RncTokenTypes.BRACES);
    if (braces.length == 2) {
      final ASTNode lbrace = braces[0];
      final ASTNode rbrace = braces[1];
      if (shouldFold(lbrace, rbrace, document)) {
        final TextRange range = new TextRange(lbrace.getStartOffset(), rbrace.getTextRange().getEndOffset());
        regions.add(new FoldingDescriptor(lbrace, range));
      }
    } else if (isAnnotation(node.getElementType())) {
      if (isOnDifferentLine(node.getFirstChildNode(), node.getLastChildNode(), document)) {
        regions.add(new FoldingDescriptor(node, node.getTextRange()));
      }
    }

    node = node.getFirstChildNode();
    while (node != null) {
      node = checkNodeAndSiblings(node, RncTokenTypes.DOC_TOKENS, regions, document);

      node = checkNodeAndSiblings(node, RncTokenTypes.COMMENTS, regions, document);

      process(node, document, regions);

      if (node != null) {
        node = node.getTreeNext();
      }
    }
  }

  @Nullable
  private static ASTNode checkNodeAndSiblings(@Nullable ASTNode node, TokenSet tokens, ArrayList<FoldingDescriptor> regions, Document document) {
    if (node != null && tokens.contains(node.getElementType())) {
      final ASTNode start = node;
      ASTNode end = start;

      node = node.getTreeNext();
      if (node != null) {
        do {
          end = node;
          node = node.getTreeNext();
        } while (node != null && tokens.contains(node.getElementType()));
      }
      if (end != start) {
        while (end.getPsi() instanceof PsiWhiteSpace) {
          end = end.getTreePrev();
        }
        if (isOnDifferentLine(start, end, document)) {
          regions.add(new FoldingDescriptor(start, new TextRange(start.getStartOffset(), end.getTextRange().getEndOffset())));
        }
      }
    }
    return node;
  }

  private static boolean shouldFold(ASTNode first, ASTNode second, Document document) {
    if (first.getElementType() != RncTokenTypes.LBRACE) {
      return false;
    } else if (second.getElementType() != RncTokenTypes.RBRACE) {
      return false;
    } else {
      return isOnDifferentLine(first, second, document);
    }
  }

  private static boolean isOnDifferentLine(ASTNode first, ASTNode second, Document document) {
    return document.getLineNumber(first.getStartOffset()) != document.getLineNumber(second.getStartOffset());
  }
}

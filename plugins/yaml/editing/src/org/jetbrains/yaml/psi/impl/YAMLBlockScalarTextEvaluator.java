// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.yaml.psi.impl.YAMLBlockScalarImplKt.isEol;

public abstract class YAMLBlockScalarTextEvaluator<T extends YAMLBlockScalarImpl> extends YamlScalarTextEvaluator<T>  {

  public YAMLBlockScalarTextEvaluator(@NotNull T host) {
    super(host);
  }
  
  protected boolean shouldIncludeEolInRange(ASTNode child) {
    if (isEol(child) && child.getTreeNext() == null && getChompingIndicator() == ChompingIndicator.KEEP) {
      return true;
    }
    return false;
  }

  protected boolean isEnding(@Nullable TextRange rangeInHost) {
    if (rangeInHost == null) return true;
    TextRange lastItem = ContainerUtil.getLastItem(getContentRanges());
    if (lastItem == null) return false;
    return rangeInHost.getEndOffset() == lastItem.getEndOffset();
  }

  @Override
  public @NotNull List<TextRange> getContentRanges() {
    final ASTNode firstContentChild = myHost.getFirstContentNode();
    if (firstContentChild == null) {
      return Collections.emptyList();
    }

    final int myStart = myHost.getTextRange().getStartOffset();
    final List<TextRange> result = new ArrayList<>();

    final int indent = myHost.locateIndent();

    final ASTNode firstEol = TreeUtil.findSibling(firstContentChild, YAMLElementTypes.EOL_ELEMENTS);
    if (firstEol == null) {
      return Collections.emptyList();
    }

    int thisLineStart = firstEol.getStartOffset() + 1;
    for (ASTNode child = firstEol.getTreeNext(); child != null; child = child.getTreeNext()) {
      final IElementType childType = child.getElementType();
      final TextRange childRange = child.getTextRange();

      if (childType == YAMLTokenTypes.INDENT && isEol(child.getTreePrev())) {
        thisLineStart = child.getStartOffset() + Math.min(indent, child.getTextLength());
      }
      else if (childType == YAMLTokenTypes.SCALAR_EOL) {
        if (thisLineStart != -1) {
          int endOffset = shouldIncludeEolInRange(child) ? child.getTextRange().getEndOffset() : child.getStartOffset();
          result.add(TextRange.create(thisLineStart, endOffset).shiftRight(-myStart));
        }
        thisLineStart = child.getStartOffset() + 1;
      }
      else {
        if (isEol(child.getTreeNext())) {
          if (thisLineStart == -1) {
            Logger.getInstance(YAMLBlockScalarTextEvaluator.class).warn("thisLineStart == -1: '" + myHost.getText() + "'", new Throwable());
            continue;
          }
          int endOffset = shouldIncludeEolInRange(child) ? child.getTreeNext().getTextRange().getEndOffset() : childRange.getEndOffset();
          result.add(TextRange.create(thisLineStart, endOffset).shiftRight(-myStart));
          thisLineStart = -1;
        }
      }
    }
    if (thisLineStart != -1) {
       result.add(TextRange.create(thisLineStart, myHost.getTextRange().getEndOffset()).shiftRight(-myStart));
    }

    ChompingIndicator chomping = getChompingIndicator();

    if (chomping == ChompingIndicator.KEEP) {
      return result;
    }

    final int lastNonEmpty = ContainerUtil.lastIndexOf(result, range -> range.getLength() != 0);

    return lastNonEmpty == -1 ? Collections.emptyList() : result.subList(0, lastNonEmpty + 1);
  }

  /**
   * See <a href="http://www.yaml.org/spec/1.2/spec.html#id2794534">8.1.1.2. Block Chomping Indicator</a>
   */
  protected final @NotNull ChompingIndicator getChompingIndicator() {
    ASTNode headerNode = myHost.getNthContentTypeChild(0);
    assert headerNode != null;

    String header = headerNode.getText();

    if (header.contains("+")) {
      return ChompingIndicator.KEEP;
    }
    if (header.contains("-")) {
      return ChompingIndicator.STRIP;
    }

    return ChompingIndicator.CLIP;
  }

  @Contract("null -> true")
  public static boolean isEolOrNull(@Nullable ASTNode node) {
    if (node == null) {
      return true;
    }
    return YAMLElementTypes.EOL_ELEMENTS.contains(node.getElementType());
  }

  /**
   * See <a href="http://www.yaml.org/spec/1.2/spec.html#id2794534">8.1.1.2. Block Chomping Indicator</a>
   */
  protected enum ChompingIndicator {
    CLIP,
    STRIP,
    KEEP
  }
}

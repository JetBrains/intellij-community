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
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLBlockScalar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class YAMLBlockScalarImpl extends YAMLScalarImpl implements YAMLBlockScalar {
  protected static final int DEFAULT_CONTENT_INDENT = 2;
  private static final int IMPLICIT_INDENT = -1;

  public YAMLBlockScalarImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  protected abstract IElementType getContentType();

  @Override
  public boolean isMultiline() {
    return true;
  }

  @NotNull
  @Override
  public String getTextValue() {
    String value = super.getTextValue();
    if (!value.isEmpty() && getChompingIndicator() != ChompingIndicator.STRIP) {
      value += "\n";
    }
    return value;
  }

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    final ASTNode firstContentChild = getFirstContentNode();
    if (firstContentChild == null) {
      return Collections.emptyList();
    }

    final int myStart = getTextRange().getStartOffset();
    final List<TextRange> result = new ArrayList<>();

    final int indent = locateIndent();

    final ASTNode firstEol = TreeUtil.findSibling(firstContentChild, YAMLTokenTypes.EOL);
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
          result.add(TextRange.create(thisLineStart, child.getStartOffset()).shiftRight(-myStart));
        }
        thisLineStart = child.getStartOffset() + 1;
      }
      else {
        if (isEol(child.getTreeNext())) {
          if (thisLineStart == -1) {
            Logger.getInstance(YAMLBlockScalarImpl.class).warn("thisLineStart == -1: '" + getText() + "'", new Throwable());
            continue;
          }
          result.add(TextRange.create(thisLineStart, childRange.getEndOffset()).shiftRight(-myStart));
          thisLineStart = -1;
        }
      }
    }
    if (thisLineStart != -1 && thisLineStart != getTextRange().getEndOffset()) {
       result.add(TextRange.create(thisLineStart, getTextRange().getEndOffset()).shiftRight(-myStart));
    }

    ChompingIndicator chomping = getChompingIndicator();

    if (chomping == ChompingIndicator.KEEP) {
      return result;
    }

    final int lastNonEmpty = ContainerUtil.lastIndexOf(result, range -> range.getLength() != 0);

    return lastNonEmpty == -1 ? Collections.emptyList() : result.subList(0, lastNonEmpty + 1);
  }

  @Override
  public boolean hasExplicitIndent() {
    return getExplicitIndent() != IMPLICIT_INDENT;
  }

  /**
   * @return Nth child of this scalar block item type ({@link YAMLElementTypes#BLOCK_SCALAR_ITEMS}).
   *         Child with number 0 is a header. Content children have numbers more than 0.
   */
  @Nullable
  public ASTNode getNthContentTypeChild(int nth) {
    int number = 0;
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == getContentType()) {
        if (number == nth) {
          return child;
        }
        number++;
      }
    }
    return null;
  }

  /** See <a href="http://www.yaml.org/spec/1.2/spec.html#id2793979">8.1.1.1. Block Indentation Indicator</a>*/
  protected final int locateIndent() {
    int indent = getExplicitIndent();
    if (indent != IMPLICIT_INDENT) {
      return indent;
    }

    ASTNode firstLine = getNthContentTypeChild(1);
    if (firstLine != null) {
      return YAMLUtil.getIndentInThisLine(firstLine.getPsi());
    }
    return 0;
  }

  /** See <a href="http://www.yaml.org/spec/1.2/spec.html#id2794534">8.1.1.2. Block Chomping Indicator</a>*/
  @NotNull
  protected final ChompingIndicator getChompingIndicator() {
    ASTNode headerNode = getNthContentTypeChild(0);
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

  private int getExplicitIndent() {
    ASTNode headerNode = getNthContentTypeChild(0);
    assert headerNode != null;

    String header = headerNode.getText();

    for (int i = 0; i < header.length(); i++) {
      if (Character.isDigit(header.charAt(i))) {
        int k = i + 1;
        // YAML 1.2 standard does not allow more then 1 symbol in indentation number
        if (k < header.length() && Character.isDigit(header.charAt(k))) {
          return IMPLICIT_INDENT;
        }
        int res = Integer.parseInt(header.substring(i, k));
        if (res == 0) {
          // zero is not allowed as c-indentation-indicator
          return IMPLICIT_INDENT;
        }
        return res;
      }
    }
    return IMPLICIT_INDENT;
  }

  @Contract("null -> false")
  private static boolean isEol(@Nullable ASTNode node) {
    if (node == null) {
      return false;
    }
    return YAMLElementTypes.EOL_ELEMENTS.contains(node.getElementType());
  }

  /** See <a href="http://www.yaml.org/spec/1.2/spec.html#id2794534">8.1.1.2. Block Chomping Indicator</a>*/
  protected enum ChompingIndicator {
    CLIP,
    STRIP,
    KEEP
  }
}

package org.jetbrains.yaml.psi.impl;

import com.intellij.codeInsight.intention.impl.QuickEditHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.UtilKt;
import kotlin.collections.CollectionsKt;
import one.util.streamex.StreamEx;
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
import java.util.Set;

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

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    int myStart = getTextRange().getStartOffset();
    int indent = locateIndent();

    List<TextRange> contentRanges = StreamEx.of(getLinesNodes()).map((line) -> {
      var first = line.get(0);
      return TextRange.create(first.getTextRange().getStartOffset() - myStart
                              + ((first.getElementType() == YAMLTokenTypes.INDENT) ? indent : 0),
                              ContainerUtil.getLastItem(line).getTextRange().getEndOffset() - myStart);
    }).toList();

    if (contentRanges.size() == 1) {
      return List.of(TextRange.create(contentRanges.get(0).getEndOffset(), contentRanges.get(0).getEndOffset()));
    }
    else if (contentRanges.isEmpty()) {
      return List.of();
    }
    else {
      return UtilKt.tailOrEmpty(contentRanges);
    }
  }

  protected int getFragmentEndOfLines(Set<QuickEditHandler> fragmentEditors) {
    if (fragmentEditors.isEmpty()) return -1;
    QuickEditHandler fe = ContainerUtil.getFirstItem(fragmentEditors);

    CharSequence cs = fe.getFragmentDocument().getImmutableCharSequence();
    int i = 0;
    for (; i < cs.length(); i++) {
      if (cs.charAt(cs.length() - i - 1) != '\n') break;
    }
    return i;
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
  public final int locateIndent() {
    int indent = getExplicitIndent();
    if (indent != IMPLICIT_INDENT) {
      return indent;
    }

    ASTNode firstLine = getNthContentTypeChild(1);
    if (firstLine != null) {
      return YAMLUtil.getIndentInThisLine(firstLine.getPsi());
    }
    else {
      List<ASTNode> line = CollectionsKt.getOrNull(getLinesNodes(), 1);
      if (line != null) {
        ASTNode lineIndentElement = ContainerUtil.find(line, l -> l.getElementType().equals(YAMLTokenTypes.INDENT));
        if (lineIndentElement != null) {
          return lineIndentElement.getTextLength();
        }
      }
    }
    return 0;
  }

  protected @NotNull List<List<ASTNode>> getLinesNodes() {
    List<List<ASTNode>> result = new SmartList<>();
    List<ASTNode> currentLine = new SmartList<>();
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      currentLine.add(child);
      if (isEol(child)) {
        result.add(currentLine);
        currentLine = new SmartList<>();
      }
    }
    if (!currentLine.isEmpty()) {
      result.add(currentLine);
    }
    return result;
  }

  /**
   * See <a href="http://www.yaml.org/spec/1.2/spec.html#id2794534">8.1.1.2. Block Chomping Indicator</a>
   */
  @NotNull
  protected final ChompingIndicator getChompingIndicator() {
    Boolean forceKeepChomping = getContainingFile().getOriginalFile().getUserData(FORCE_KEEP_CHOMPING);
    if (forceKeepChomping != null && forceKeepChomping) {
      return ChompingIndicator.KEEP;
    }
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
  public static boolean isEol(@Nullable ASTNode node) {
    if (node == null) {
      return false;
    }
    return YAMLElementTypes.EOL_ELEMENTS.contains(node.getElementType());
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

package com.intellij.psi.formatter.xml;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AnotherLanguageBlockWrapper extends AbstractXmlBlock{
  private final Block myOriginal;
  private final Indent myIndent;
  private final int myOffset;

  public AnotherLanguageBlockWrapper(final ASTNode node,
                                     final XmlFormattingPolicy policy,
                                     final Block original, final Indent indent, final int offset) {
    super(node, original.getWrap(), original.getAlignment(), policy);
    myOriginal = original;
    myIndent = indent;
    myOffset = offset;
  }

  public Indent getIndent() {
    return myIndent;
  }

  public boolean insertLineBreakBeforeTag() {
    return false;
  }

  public boolean removeLineBreakBeforeTag() {
    return false;
  }

  public boolean isTextElement() {
    return true;
  }

  protected List<Block> buildChildren() {
    final List<Block> list = myOriginal.getSubBlocks();
    if (myOffset == 0) return list;

    return InjectedLanguageBlockWrapper.buildBlocks(myOriginal, myOffset);
  }

  @NotNull
  public TextRange getTextRange() {
    final TextRange range = super.getTextRange();
    if (myOffset == 0) return range;
    return new TextRange(myOffset + range.getStartOffset(), myOffset + range.getEndOffset());
  }

  @Nullable public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof InjectedLanguageBlockWrapper) child1 = ((InjectedLanguageBlockWrapper)child1).myOriginal;
    if (child2 instanceof InjectedLanguageBlockWrapper) child2 = ((InjectedLanguageBlockWrapper)child2).myOriginal;
    return myOriginal.getSpacing(child1,  child2);
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }
}

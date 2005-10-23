package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class AnotherLanguageBlockWrapper extends AbstractXmlBlock{
  private final Block myOriginal;
  private final Indent myIndent;

  public AnotherLanguageBlockWrapper(final ASTNode node,
                                     final XmlFormattingPolicy policy,
                                     final Block original, final Indent indent) {
    super(node, original.getWrap(), original.getAlignment(), policy);
    myOriginal = original;
    myIndent = indent;
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
    return myOriginal.getSubBlocks();
  }

  @Nullable public Spacing getSpacing(Block child1, Block child2) {
    return myOriginal.getSpacing(child1,  child2);
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }
}

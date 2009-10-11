/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Nullable private final TextRange myRange;

  public AnotherLanguageBlockWrapper(final ASTNode node,
                                     final XmlFormattingPolicy policy,
                                     final Block original, final Indent indent,
                                     final int offset,
                                     @Nullable TextRange range) {
    super(node, original.getWrap(), original.getAlignment(), policy);
    myOriginal = original;
    myIndent = indent;
    myOffset = offset;
    myRange = range;
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
    if (myOffset == 0 && myRange == null) return myOriginal.getSubBlocks();

    return InjectedLanguageBlockWrapper.buildBlocks(myOriginal, myOffset, myRange);
  }

  @NotNull
  public TextRange getTextRange() {
    final TextRange range = super.getTextRange();
    if (myOffset == 0) return range;
    
    return new TextRange(
        myOffset + range.getStartOffset(),
        myOffset + (myRange != null ? myRange.getLength() : range.getLength())
    );
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

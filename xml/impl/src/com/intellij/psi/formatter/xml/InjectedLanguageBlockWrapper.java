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

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class InjectedLanguageBlockWrapper implements Block {
  final Block myOriginal;
  private final int myOffset;
  private List<Block> myBlocks;

  public InjectedLanguageBlockWrapper(final Block original, final int offset) {
    myOriginal = original;
    myOffset = offset;
  }

  public Indent getIndent() {
    return myOriginal.getIndent();
  }

  @Nullable
  public Alignment getAlignment() {
    return myOriginal.getAlignment();
  }

  @NotNull
  public TextRange getTextRange() {
    final TextRange range = myOriginal.getTextRange();
    return new TextRange(myOffset + range.getStartOffset(), myOffset + range.getEndOffset());
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (myBlocks == null) {
      myBlocks = buildBlocks(myOriginal, myOffset, null);
    }
    return myBlocks;
  }

  static List<Block> buildBlocks(Block myOriginal, int myOffset, TextRange range) {
    final List<Block> list = myOriginal.getSubBlocks();
    if (list.size() == 0) return AbstractXmlBlock.EMPTY;
    else {
      final ArrayList<Block> result = new ArrayList<Block>(list.size());
      if (range == null) {
        for(Block b:list) result.add(new InjectedLanguageBlockWrapper(b, myOffset));
      } else {
        collectBlocksIntersectingRange(list, result, range, myOffset);
      }
      return result;
    }
  }

  private static void collectBlocksIntersectingRange(final List<Block> list, final List<Block> result, final TextRange range,
                                                     int blockStartOffset) {
    for(Block b:list) {
      final TextRange textRange = b.getTextRange();
      if (range.contains(textRange)) {
        result.add(new InjectedLanguageBlockWrapper(b, blockStartOffset - range.getStartOffset()));
      } else if (textRange.intersectsStrict(range)) {
        collectBlocksIntersectingRange(b.getSubBlocks(), result, range, blockStartOffset);
      }
    }
  }

  @Nullable
  public Wrap getWrap() {
    return myOriginal.getWrap();
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

  public boolean isIncomplete() {
    return myOriginal.isIncomplete();
  }

  public boolean isLeaf() {
    return myOriginal.isLeaf();
  }
}
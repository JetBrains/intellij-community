/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.template.formatter;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CompositeTemplateBlock implements Block {
  private final List<Block> mySubBlocks;
  private final TextRange myTextRange;

  public CompositeTemplateBlock(List<Block> subBlocks) {
    mySubBlocks = subBlocks;
    myTextRange = new TextRange(mySubBlocks.get(0).getTextRange().getStartOffset(),
                                mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  public CompositeTemplateBlock(@NotNull TextRange range) {
    mySubBlocks = Collections.emptyList();
    myTextRange = range;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myTextRange;
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  @Override
  public Wrap getWrap() {
    return null;
  }

  @Override
  public Indent getIndent() {
    return Indent.getNoneIndent();
  }

  @Override
  public Alignment getAlignment() {
    return null;
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(null, null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}

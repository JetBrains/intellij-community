/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.InjectedLanguageBlockWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AnotherLanguageBlockWrapper extends AbstractXmlBlock {
  private final InjectedLanguageBlockWrapper myInjectedBlock;
  private final Indent myIndent;

  public AnotherLanguageBlockWrapper(final ASTNode node,
                                     final XmlFormattingPolicy policy,
                                     final Block original, final Indent indent,
                                     final int offset,
                                     @Nullable TextRange range) {
    super(node, original.getWrap(), original.getAlignment(), policy, false);
    myInjectedBlock = new InjectedLanguageBlockWrapper(original, offset, range, null);
    myIndent = indent;
  }

  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  public boolean insertLineBreakBeforeTag() {
    return false;
  }

  @Override
  public boolean removeLineBreakBeforeTag() {
    return false;
  }

  @Override
  public boolean isTextElement() {
    return true;
  }

  @Override
  protected List<Block> buildChildren() {
    return myInjectedBlock.getSubBlocks();
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myInjectedBlock.getTextRange();
  }

  @Override
  @Nullable
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return myInjectedBlock.getSpacing(child1,  child2);
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myInjectedBlock.getChildAttributes(newChildIndex);
  }
}

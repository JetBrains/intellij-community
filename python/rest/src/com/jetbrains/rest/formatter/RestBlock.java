/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.formatter;


import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.RestElementTypes;
import com.jetbrains.rest.RestTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RestBlock implements ASTBlock {
  private final ASTNode myNode;
  private final Alignment myAlignment;
  private final Indent myIndent;
  private final Wrap myWrap;
  private List<RestBlock> mySubBlocks = null;

  public RestBlock(ASTNode node, final Alignment alignment, Indent indent, Wrap wrap) {
    myNode = node;
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
  }


  @Override
  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildSubBlocks();
    }
    return new ArrayList<>(mySubBlocks);
  }
  private List<RestBlock> buildSubBlocks() {
    List<RestBlock> blocks = new ArrayList<>();
    for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {

      IElementType childType = child.getElementType();
      if (child.getTextRange().getLength() == 0) continue;
      if (childType == RestTokenTypes.WHITESPACE) {
        continue;
      }

      blocks.add(buildSubBlock(child));
    }
    return Collections.unmodifiableList(blocks);
  }

  private RestBlock buildSubBlock(ASTNode child) {
    IElementType childType = child.getElementType();
    IElementType grandparentType = myNode.getTreeParent() == null ? null : myNode.getTreeParent().getElementType();
    Indent childIndent = Indent.getNoneIndent();

    if (grandparentType == RestElementTypes.DIRECTIVE_BLOCK && childType == RestTokenTypes.FIELD) {
      childIndent = Indent.getNormalIndent();
    }
    return new RestBlock(child, null, childIndent, null);
  }


  @Nullable
  @Override
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  @Override
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  @Override
  public Alignment getAlignment() {
    return myAlignment;
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @NotNull
  @Override
  public ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return false;
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }
}

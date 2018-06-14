// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;

import java.util.List;

class YAMLFormattingBlock extends AbstractBlock {
  @NotNull
  private final YAMLFormattingContext myContext;
  @Nullable
  private final Indent myIndent;
  @Nullable
  private final Indent myNewChildIndent;

  private final boolean myIsIncomplete;

  YAMLFormattingBlock(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    super(node, null, context.computeAlignment(node));
    myContext = context;

    myIndent = myContext.computeBlockIndent(myNode);
    myIsIncomplete = myContext.isIncomplete(myNode);
    myNewChildIndent = myContext.computeNewChildIndent(myNode);
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return myContext.computeSpacing(child1, child2);
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Override
  public boolean isIncomplete() {
    return myIsIncomplete;
  }

  @Override
  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  @Override
  protected Indent getChildIndent() {
    return myNewChildIndent;
  }

  @Override
  protected List<Block> buildChildren() {
    return buildSubBlocks(myContext, myNode);
  }

  @NotNull
  private static List<Block> buildSubBlocks(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    List<Block> res = new SmartList<>();
    for (ASTNode subNode = node.getFirstChildNode(); subNode != null; subNode = subNode.getTreeNext()) {
      IElementType subNodeType = PsiUtilCore.getElementType(subNode);
      if (YAMLElementTypes.SPACE_ELEMENTS.contains(subNodeType)) {
        // just skip them (comment processed above)
      }
      else if (YAMLElementTypes.CONTAINERS.contains(subNodeType)) {
        res.addAll(buildSubBlocks(context, subNode));
      }
      else {
        res.add(YAMLFormattingModelBuilder.createBlock(context, subNode));
      }
    }
    return res;
  }
}

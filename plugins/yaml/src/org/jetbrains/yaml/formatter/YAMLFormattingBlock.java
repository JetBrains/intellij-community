// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;

import java.util.List;

class YAMLFormattingBlock extends AbstractBlock {
  private final @NotNull YAMLFormattingContext myContext;
  private final @Nullable Indent myIndent;
  private final @Nullable Indent myNewChildIndent;

  private final boolean myIsIncomplete;

  private final @NotNull TextRange myTextRange;

  YAMLFormattingBlock(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    super(node, null, context.computeAlignment(node));
    myContext = context;

    myIndent = myContext.computeBlockIndent(myNode);
    myIsIncomplete = myContext.isIncomplete(myNode);
    myNewChildIndent = myContext.computeNewChildIndent(myNode);
    myTextRange = myNode.getTextRange();
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return myContext.computeSpacing(this, child1, child2);
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
  public @Nullable Indent getIndent() {
    return myIndent;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myTextRange;
  }

  @Override
  protected @Nullable Indent getChildIndent() {
    return myNewChildIndent;
  }

  @Override
  protected List<Block> buildChildren() {
    return buildSubBlocks(myContext, myNode);
  }

  private @NotNull List<Block> buildSubBlocks(@NotNull YAMLFormattingContext context, @NotNull ASTNode node) {
    List<Block> res = new SmartList<>();
    for (ASTNode subNode = node.getFirstChildNode(); subNode != null; subNode = subNode.getTreeNext()) {
      IElementType subNodeType = PsiUtilCore.getElementType(subNode);
      if (YAMLElementTypes.SPACE_ELEMENTS.contains(subNodeType)) {
        // just skip them (comment processed above)
      }
      else if (YAMLElementTypes.SCALAR_QUOTED_STRING == subNodeType) {
        res.addAll(buildSubBlocks(context, subNode));
      }
      else if (YAMLElementTypes.CONTAINERS.contains(subNodeType)) {
        res.addAll(YamlInjectedBlockFactory.substituteInjectedBlocks(
          context.mySettings,
          buildSubBlocks(context, subNode),
          subNode, getWrap(), context.computeAlignment(subNode)
        ));
      }
      else {
        res.add(YAMLFormattingModelBuilder.createBlock(context, subNode));
      }
    }
    return res;
  }
}

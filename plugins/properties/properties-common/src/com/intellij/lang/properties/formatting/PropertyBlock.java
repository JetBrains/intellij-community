// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.formatting;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class PropertyBlock extends AbstractBlock {
  protected PropertyBlock(@NotNull ASTNode node,
                          @Nullable Alignment alignment) {
    super(node, null, alignment);
  }

  @Override
  protected List<Block> buildChildren() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  public Indent getIndent() {
    return Indent.getAbsoluteNoneIndent();
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyBinaryExpressionBlock extends PyBlock {

  private List<ASTNode> myChildrenNodes = new ArrayList<>();

  public PyBinaryExpressionBlock(@Nullable PyBlock parent,
                                 @NotNull ASTNode node,
                                 @Nullable Alignment alignment,
                                 @NotNull Indent indent,
                                 @Nullable Wrap wrap,
                                 @NotNull PyBlockContext context) {
    super(parent, node, alignment, indent, wrap, context);
    assert node.getElementType() == PyElementTypes.BINARY_EXPRESSION;

    myChildrenNodes = collectChildren();
  }

  @NotNull
  private List<ASTNode> collectChildren() {
    List<ASTNode> result = new ArrayList<>();
    collectChildren(myNode, result);
    return result;
  }

  private void collectChildren(@NotNull ASTNode node, @NotNull List<ASTNode> result) {
    if (node.getElementType() == PyElementTypes.BINARY_EXPRESSION) {
      for (ASTNode child : node.getChildren(null)) {
        collectChildren(child, result);
      }
    }
    else if (node != this) {
      result.add(node);
    }
  }

  @NotNull
  @Override
  protected Iterable<ASTNode> getSubBlockNodes() {
    return myChildrenNodes;
  }
}

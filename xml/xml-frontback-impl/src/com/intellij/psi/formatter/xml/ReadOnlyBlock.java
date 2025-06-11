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

import com.intellij.lang.ASTNode;
import com.intellij.formatting.Block;
import com.intellij.formatting.Spacing;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReadOnlyBlock extends AbstractBlock {
  private static final ArrayList<Block> EMPTY = new ArrayList<>();

  public ReadOnlyBlock(ASTNode node) {
    super(node, null, null);
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Override
  protected List<Block> buildChildren() {
    return EMPTY;
  }
}

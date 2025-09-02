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

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.formatter.xml.SyntheticBlock;
import com.intellij.psi.formatter.xml.XmlFormattingPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TemplateSyntheticBlock extends SyntheticBlock implements IndentInheritingBlock {
  private Indent myInheritedIndent;

  public TemplateSyntheticBlock(final List<Block> subBlocks,
                                final Block parent,
                                final Indent indent,
                                XmlFormattingPolicy policy,
                                final Indent childIndent) {
    super(subBlocks, parent, indent, policy, childIndent);
  }

  @Override
  public void setIndent(Indent indent) {
    myInheritedIndent = indent;
  }

  @Override
  public Indent getIndent() {
    return myInheritedIndent != null ? myInheritedIndent : super.getIndent();
  }

  @Override
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    if (child1 != null && isXmlBlock(child1) != isXmlBlock(child2)) {
      if (shouldKeepWhiteSpacesInside()) return Spacing.getReadOnlySpacing();
      return Spacing.createSpacing(0, 1, 0, true, myXmlFormattingPolicy.getKeepBlankLines());
    }
    return super.getSpacing(child1, child2);
  }

  private static boolean isXmlBlock(@NotNull Block block) {
    if (block instanceof TemplateXmlTagBlock || block instanceof TemplateXmlBlock) return true;
    if (block instanceof ASTBlock) {
      ASTNode node = ((ASTBlock)block).getNode();
      return node != null && node.getPsi().getLanguage().isKindOf(XMLLanguage.INSTANCE);
    }
    return false;
  }
}

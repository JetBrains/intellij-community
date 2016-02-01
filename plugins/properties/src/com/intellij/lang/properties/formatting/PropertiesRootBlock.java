/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.properties.formatting;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.parsing.PropertyListStubElementType;
import com.intellij.lang.properties.parsing.PropertyStubElementType;
import com.intellij.lang.properties.psi.codeStyle.PropertiesCodeStyleSettings;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesRootBlock extends AbstractBlock {

  private final CodeStyleSettings mySettings;
  private Alignment mySeparatorAlignment;

  protected PropertiesRootBlock(@NotNull ASTNode node,
                                @Nullable Wrap wrap, CodeStyleSettings settings) {
    super(node, wrap, Alignment.createAlignment());
    mySettings = settings;
    mySeparatorAlignment = Alignment.createAlignment(true, Alignment.Anchor.LEFT);
  }

  @Override
  protected List<Block> buildChildren() {
    final List<Block> result = new ArrayList<>();
    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (!(child instanceof PsiWhiteSpace)) {
        if (child.getElementType() instanceof PropertyListStubElementType) {
          ASTNode propertyNode = child.getFirstChildNode();
          while (propertyNode != null) {
            if (propertyNode.getElementType() instanceof PropertyStubElementType) {
              collectPropertyBlock(propertyNode, result);
            }
            else if (PropertiesTokenTypes.END_OF_LINE_COMMENT.equals(propertyNode.getElementType())) {
              result.add(new PropertyBlock(propertyNode, null));
            }
            propertyNode = propertyNode.getTreeNext();
          }
          break;
        }
      }
      if (PropertiesTokenTypes.END_OF_LINE_COMMENT.equals(child.getElementType())) {
        result.add(new PropertyBlock(child, null));
      }
      child = child.getTreeNext();
    }
    return result;
  }

  private void collectPropertyBlock(ASTNode propertyNode, List<Block> collector) {
    final ASTNode key = propertyNode.getFirstChildNode();
    if (key instanceof PropertyKeyImpl) {
      collector.add(new PropertyBlock(key, null));
      final ASTNode separator = key.getTreeNext();
      if (separator != null && PropertiesTokenTypes.KEY_VALUE_SEPARATOR.equals(separator.getElementType())) {
        collector.add(new SeparatorBlock(separator, mySettings.ALIGN_GROUP_FIELD_DECLARATIONS ? mySeparatorAlignment : null));
        final ASTNode value = separator.getTreeNext();
        if (value instanceof PropertyValueImpl) {
          collector.add(new PropertyBlock(value, null));
        }
      }
    }
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    return mySettings.getCustomSettings(PropertiesCodeStyleSettings.class).SPACES_AROUND_KEY_VALUE_DELIMITER &&
           (child1 instanceof SeparatorBlock || child2 instanceof SeparatorBlock)
           ? Spacing.createSpacing(1, 1, 0, true, 0)
           : Spacing.createSpacing(0, 0, 0, true, 0);
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}

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

import com.intellij.formatting.*;
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
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.io.File.separator;

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
            else if (PropertiesTokenTypes.END_OF_LINE_COMMENT.equals(propertyNode.getElementType()) ||
                     PropertiesTokenTypes.BAD_CHARACTER.equals(propertyNode.getElementType())) {
              result.add(new PropertyBlock(propertyNode, null));
            }
            propertyNode = propertyNode.getTreeNext();
          }
        }
        else if (PropertiesTokenTypes.BAD_CHARACTER.equals(child.getElementType())) {
          result.add(new PropertyBlock(child, null));
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
    final ASTNode[] nonWhiteSpaces = propertyNode.getChildren(TokenSet.create(PropertiesTokenTypes.KEY_CHARACTERS,
                                                                              PropertiesTokenTypes.KEY_VALUE_SEPARATOR,
                                                                              PropertiesTokenTypes.VALUE_CHARACTERS));
    for (ASTNode node : nonWhiteSpaces) {
      if (node instanceof PropertyKeyImpl) {
        collector.add(new PropertyBlock(node, null));
      }
      if (PropertiesTokenTypes.KEY_VALUE_SEPARATOR.equals(node.getElementType())) {
        collector.add(new PropertyBlock(node, mySettings.ALIGN_GROUP_FIELD_DECLARATIONS ? mySeparatorAlignment : null));
      }
      if (node instanceof PropertyValueImpl) {
        collector.add(new PropertyBlock(node, null));
      }
    }
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    if (child1 == null) {
      return null;
    }
    return (mySettings.getCustomSettings(PropertiesCodeStyleSettings.class).SPACES_AROUND_KEY_VALUE_DELIMITER &&
            (isSeparator(child1) || isSeparator(child2))) || isKeyValue(child1, child2)
           ? Spacing.createSpacing(1, 1, 0, true, 0)
           : Spacing.createSpacing(0, 0, 0, true,
                                   mySettings.getCustomSettings(PropertiesCodeStyleSettings.class).KEEP_BLANK_LINES ? 999 : 0);
  }

  private static boolean isKeyValue(Block maybeKey, Block maybeValue) {
    if (!(maybeKey instanceof PropertyBlock) ||
        !PropertiesTokenTypes.KEY_CHARACTERS.equals(((PropertyBlock)maybeKey).getNode().getElementType())) {
      return false;
    }
    if (!(maybeValue instanceof PropertyBlock) ||
        !PropertiesTokenTypes.VALUE_CHARACTERS.equals(((PropertyBlock)maybeValue).getNode().getElementType())) {
      return false;
    }
    return true;
  }

  private static boolean isSeparator(Block block) {
    return block instanceof PropertyBlock &&
           PropertiesTokenTypes.KEY_VALUE_SEPARATOR.equals(((PropertyBlock)block).getNode().getElementType());
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}

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
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
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

/**
 * @author Dmitry Batkovich
 */
class PropertiesRootBlock extends AbstractBlock {
  private final CodeStyleSettings mySettings;
  private final Alignment mySeparatorAlignment;

  PropertiesRootBlock(@NotNull ASTNode node,
                      CodeStyleSettings settings) {
    super(node, null, Alignment.createAlignment());
    mySettings = settings;
    mySeparatorAlignment = Alignment.createAlignment(true, Alignment.Anchor.LEFT);
  }

  @Override
  protected List<Block> buildChildren() {
    final List<Block> result = new ArrayList<>();
    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (!(child instanceof PsiWhiteSpace)) {
        if (child.getElementType() == PropertiesElementTypes.PROPERTIES_LIST) {
          ASTNode propertyNode = child.getFirstChildNode();
          while (propertyNode != null) {
            if (propertyNode.getElementType() == PropertiesElementTypes.PROPERTY) {
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

  @Override
  protected @Nullable Indent getChildIndent() {
    return Indent.getNoneIndent();
  }

  private void collectPropertyBlock(ASTNode propertyNode, List<? super Block> collector) {
    final ASTNode[] nonWhiteSpaces = propertyNode.getChildren(TokenSet.create(PropertiesTokenTypes.KEY_CHARACTERS,
                                                                              PropertiesTokenTypes.KEY_VALUE_SEPARATOR,
                                                                              PropertiesTokenTypes.VALUE_CHARACTERS));
    final Alignment alignment = mySettings.getCommonSettings(PropertiesLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS
                                ? mySeparatorAlignment
                                : null;

    boolean hasKVSeparator = false;
    for (ASTNode node : nonWhiteSpaces) {
      if (node instanceof PropertyKeyImpl) {
        collector.add(new PropertyBlock(node, null));
      }
      else if (PropertiesTokenTypes.KEY_VALUE_SEPARATOR.equals(node.getElementType())) {
        collector.add(new PropertyBlock(node, alignment));
        hasKVSeparator = true;
      }
      else if (node instanceof PropertyValueImpl) {
        if (hasKVSeparator) {
          collector.add(new PropertyBlock(node, null));
        }
        else {
          collector.add(new PropertyBlock(node, alignment));
        }
      }
    }
  }

  @Nullable
  @Override
  public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
    if (child1 == null) {
      return null;
    }
    return mySettings.getCustomSettings(PropertiesCodeStyleSettings.class).SPACES_AROUND_KEY_VALUE_DELIMITER &&
            (isSeparator(child1) || isSeparator(child2)) || isKeyValue(child1, child2)
           ? Spacing.createSpacing(1, 1, 0, true, 0)
           : Spacing.createSpacing(0, 0, 0, true,
                                   mySettings.getCustomSettings(PropertiesCodeStyleSettings.class).KEEP_BLANK_LINES ? 999 : 0);
  }

  private static boolean isKeyValue(Block maybeKey, Block maybeValue) {
    if (!(maybeKey instanceof PropertyBlock) ||
        !PropertiesTokenTypes.KEY_CHARACTERS.equals(((PropertyBlock)maybeKey).getNode().getElementType())) {
      return false;
    }
    return maybeValue instanceof PropertyBlock &&
           PropertiesTokenTypes.VALUE_CHARACTERS.equals(((PropertyBlock)maybeValue).getNode().getElementType());
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

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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class SeparatorBlock extends PropertyBlock {
  private final static Logger LOG = Logger.getInstance(SeparatorBlock.class);

  protected SeparatorBlock(@NotNull ASTNode node,
                           @Nullable Alignment alignment) {
    super(node, alignment);
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    final String nodeText = myNode.getText();
    int separatorLocalOffset = StringUtil.indexOfAny(nodeText, "=:");
    if (separatorLocalOffset == -1 && !nodeText.isEmpty() && nodeText.charAt(0) == ' ') {
      separatorLocalOffset = 0;
    }
    LOG.assertTrue(separatorLocalOffset > -1, "Invalid separator \"" + myNode.getText() + "\'");
    final int separatorOffset = myNode.getStartOffset() + separatorLocalOffset;
    return new TextRange(separatorOffset, separatorOffset + 1);
  }
}

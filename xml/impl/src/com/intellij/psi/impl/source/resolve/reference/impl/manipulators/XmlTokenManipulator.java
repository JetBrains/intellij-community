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
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.lang.ASTFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class XmlTokenManipulator extends AbstractElementManipulator<XmlToken> {
  @Override
  public XmlToken handleContentChange(@NotNull XmlToken xmlToken, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = xmlToken.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    IElementType tokenType = xmlToken.getTokenType();

    FileElement holder = DummyHolderFactory.createHolder(xmlToken.getManager(), null).getTreeElement();
    LeafElement leaf = ASTFactory.leaf(tokenType, holder.getCharTable().intern(newText));
    holder.rawAddChildren(leaf);
    return (XmlToken)xmlToken.replace(leaf.getPsi());
  }
}

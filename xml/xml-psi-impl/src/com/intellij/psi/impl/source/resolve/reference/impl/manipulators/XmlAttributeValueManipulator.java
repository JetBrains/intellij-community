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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 20:00:23
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueManipulator extends AbstractElementManipulator<XmlAttributeValue> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator");

  @Override
  public XmlAttributeValue handleContentChange(@NotNull XmlAttributeValue element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    return handleContentChange(element, range, newContent, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN);
  }

  public static <T extends PsiElement> T handleContentChange(T element,
                                                             TextRange range,
                                                             String newContent,
                                                             final IElementType tokenType) {
    CheckUtil.checkWritable(element);
    final CompositeElement attrNode = (CompositeElement)element.getNode();
    final ASTNode valueNode = attrNode.findLeafElementAt(range.getStartOffset());
    LOG.assertTrue(valueNode != null, "Leaf not found in " + attrNode + " at offset " + range.getStartOffset() + " in element " + element);
    final PsiElement elementToReplace = valueNode.getPsi();

    String text;
    try {
      text = elementToReplace.getText();
      final int offsetInParent = elementToReplace.getStartOffsetInParent();
      String textBeforeRange = text.substring(0, range.getStartOffset() - offsetInParent);
      String textAfterRange = text.substring(range.getEndOffset()- offsetInParent, text.length());
      newContent = element.getText().startsWith("'") || element.getText().endsWith("'") ?
                   newContent.replace("'", "&apos;") : newContent.replace("\"", "&quot;");
      text = textBeforeRange + newContent + textAfterRange;
    } catch(StringIndexOutOfBoundsException e) {
      LOG.error("Range: " + range + " in text: '" + element.getText() + "'", e);
      throw e;
    }
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(attrNode);
    final LeafElement newValueElement = Factory.createSingleLeafElement(tokenType, text, charTableByTree, element.getManager());

    attrNode.replaceChildInternal(valueNode, newValueElement);
    return element;
  }

  @Override
  @NotNull
  public TextRange getRangeInElement(@NotNull final XmlAttributeValue xmlAttributeValue) {
    final PsiElement first = xmlAttributeValue.getFirstChild();
    if (first == null) {
      return TextRange.EMPTY_RANGE;
    }
    final ASTNode firstNode = first.getNode();
    assert firstNode != null;
    final PsiElement last = xmlAttributeValue.getLastChild();
    final ASTNode lastNode = last != null && last != first ? last.getNode() : null;

    final int textLength = xmlAttributeValue.getTextLength();
    final int start = firstNode.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER ? first.getTextLength() : 0;
    final int end = lastNode != null && lastNode.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER ? last.getTextLength() : 0;
    return new TextRange(start, textLength - end);
  }
}

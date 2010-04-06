/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 20:00:23
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueManipulator extends AbstractElementManipulator<XmlAttributeValue> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.manipulators.XmlAttributeValueManipulator");

  public XmlAttributeValue handleContentChange(XmlAttributeValue element, TextRange range, String newContent) throws IncorrectOperationException{

    CheckUtil.checkWritable(element);
    final CompositeElement attrNode = (CompositeElement)element.getNode();
    final ASTNode valueNode = attrNode.findLeafElementAt(range.getStartOffset());
    LOG.assertTrue(valueNode != null, "Leaf not found in " + attrNode + " at offset " + range.getStartOffset());
    final PsiElement elementToReplace = valueNode.getPsi();

    String text;
    try {
      text = elementToReplace.getText();
      final int offsetInParent = elementToReplace.getStartOffsetInParent();
      String textBeforeRange = text.substring(0, range.getStartOffset() - offsetInParent);
      String textAfterRange = text.substring(range.getEndOffset()- offsetInParent, text.length());
      text = textBeforeRange + newContent + textAfterRange;
    } catch(StringIndexOutOfBoundsException e) {
      LOG.error("Range: " + range + " in text: '" + element.getText() + "'", e);
      throw e;
    }
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(attrNode);
    final LeafElement newValueElement = Factory.createSingleLeafElement(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, text, charTableByTree, element.getManager());

    attrNode.replaceChildInternal(valueNode, newValueElement);
    return element;
  }

  public TextRange getRangeInElement(final XmlAttributeValue xmlAttributeValue) {
    final PsiElement child = xmlAttributeValue.getFirstChild();
    if (child == null) {
      return new TextRange(0, 0);
    }
    final ASTNode node = child.getNode();
    assert node != null;
    final int textLength = xmlAttributeValue.getTextLength();
    if (node.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      return new TextRange(1, textLength <= 1 ? 1 : textLength - 1);
    } else {
      return new TextRange(0, textLength);
    }
  }
}

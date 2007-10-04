package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.tree.*;
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
    final LeafElement newValueElement = Factory.createSingleLeafElement(
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
      text,
      0,
      text.length(), charTableByTree, element.getManager());

    attrNode.replaceChildInternal(valueNode, newValueElement);
    return element;
  }

  public TextRange getRangeInElement(final XmlAttributeValue xmlAttributeValue) {
    final int textLength = xmlAttributeValue.getTextLength();
    return textLength < 2 ? new TextRange(0, 0) : new TextRange(1, textLength - 1);
  }
}

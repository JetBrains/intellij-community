package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.resolve.reference.AbstractElementManipulator;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class XmlTokenManipulator extends AbstractElementManipulator<XmlToken> {
  public XmlToken handleContentChange(XmlToken xmlToken, TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = xmlToken.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    IElementType tokenType = xmlToken.getTokenType();
    char[] buffer = newText.toCharArray();
    FileElement holder = new DummyHolder(xmlToken.getManager(), null).getTreeElement();
    LeafElement leaf = Factory.createLeafElement(tokenType, buffer, 0, buffer.length, -1, holder.getCharTable());
    TreeUtil.addChildren(holder, leaf);
    return (XmlToken)xmlToken.replace(leaf.getPsi());
  }
}

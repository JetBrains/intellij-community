package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.lang.ASTFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.impl.source.DummyHolderFactory;
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

    FileElement holder = DummyHolderFactory.createHolder(xmlToken.getManager(), null).getTreeElement();
    LeafElement leaf = ASTFactory.leaf(tokenType, newText, 0, newText.length(), holder.getCharTable());
    TreeUtil.addChildren(holder, leaf);
    return (XmlToken)xmlToken.replace(leaf.getPsi());
  }
}

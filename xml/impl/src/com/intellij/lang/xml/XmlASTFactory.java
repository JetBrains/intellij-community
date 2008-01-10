/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public class XmlASTFactory extends ASTFactory {
  public CompositeElement createComposite(final IElementType type) {
    return Factory.createCompositeElement(type);
  }

  public LeafElement createLeaf(final IElementType type, final CharSequence fileText, final int start, final int end, final CharTable table) {
    return Factory.createLeafElement(type, fileText, start, end, table);
  }
}
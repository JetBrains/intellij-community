package com.intellij.psi.tree;

import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.diagnostic.Logger;

public class DefaultRoleFinder implements RoleFinder{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.DefaultRoleFinder");
  final IElementType myElementType;
  final IElementType myParentType;

  public DefaultRoleFinder(IElementType elementType, IElementType parentType) {
    myElementType = elementType;
    myParentType = parentType;
  }

  public TreeElement findChild(CompositeElement parent) {
    if(myParentType != null) LOG.assertTrue(parent.getElementType() == myParentType);
    TreeElement current = parent.firstChild;
    while(current != null && current.getElementType() != myElementType)
      current = current.getTreeNext();
    return current;
  }
}

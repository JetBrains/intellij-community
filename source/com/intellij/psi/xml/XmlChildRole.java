package com.intellij.psi.xml;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.RoleFinder;
import com.intellij.psi.tree.DefaultRoleFinder;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.openapi.diagnostic.Logger;

public class XmlChildRole {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.xml.XmlChildRole");

  public static final RoleFinder START_TAG_NAME_FINDER = new RoleFinder() {
    public LeafElement findChild(CompositeElement parent) {
      //LOG.assertTrue(parent.getElementType() == XmlElementType.XML_TAG);
      TreeElement current = parent.firstChild;
      IElementType elementType;
      while(current != null
            && (elementType = current.getElementType()) != XmlTokenType.XML_NAME
            && elementType != XmlTokenType.XML_TAG_NAME){
        current = current.getTreeNext();
      }
      return (LeafElement)current;
    }
  };

  public static final RoleFinder CLOSING_TAG_NAME_FINDER = new RoleFinder() {
    public TreeElement findChild(CompositeElement parent) {
      //LOG.assertTrue(parent.getElementType() == XmlElementType.XML_TAG);
      TreeElement current = parent.firstChild;
      int state = 0;
      while(current != null){
        final IElementType elementType = current.getElementType();
        switch(state){
          case 0:
            if(elementType == XmlTokenType.XML_END_TAG_START) state = 1;
            break;
          case 1:
            if(elementType == XmlTokenType.XML_NAME || elementType == XmlTokenType.XML_TAG_NAME)
              return current;
        }
        current = current.getTreeNext();
      }
      return current;
    }
  };

  public static final RoleFinder CLOSING_TAG_START_FINDER = new DefaultRoleFinder(XmlTokenType.XML_END_TAG_START, null);
  public static final RoleFinder EMPTY_TAG_END_FINDER = new DefaultRoleFinder(XmlTokenType.XML_EMPTY_ELEMENT_END, null);
  public static final RoleFinder ATTRIBUTE_NAME_FINDER = new DefaultRoleFinder(XmlTokenType.XML_NAME, null);
  public static final RoleFinder ATTRIBUTE_VALUE_FINDER = new DefaultRoleFinder(XmlElementType.XML_ATTRIBUTE_VALUE, null);
  public static final RoleFinder START_TAG_END_FINDER = new DefaultRoleFinder(XmlTokenType.XML_TAG_END, null);
}

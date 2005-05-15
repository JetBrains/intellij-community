package com.intellij.psi.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.DefaultRoleFinder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.RoleFinder;

public class XmlChildRole {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.xml.XmlChildRole");

  public static final RoleFinder START_TAG_NAME_FINDER = new RoleFinder() {
    public ASTNode findChild(ASTNode parent) {
      //LOG.assertTrue(parent.getElementType() == XmlElementType.XML_TAG);
      ASTNode current = parent.getFirstChildNode();
      IElementType elementType;
      while(current != null
            && (elementType = current.getElementType()) != XmlTokenType.XML_NAME
            && elementType != XmlTokenType.XML_TAG_NAME){
        current = current.getTreeNext();
      }
      return current;
    }
  };

  public static final RoleFinder CLOSING_TAG_NAME_FINDER = new RoleFinder() {
    public ASTNode findChild(ASTNode parent) {
      //LOG.assertTrue(parent.getElementType() == XmlElementType.XML_TAG);
      ASTNode current = parent.getFirstChildNode();
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

  public static final RoleFinder DOCUMENT_FINDER = new RoleFinder() {
    public ASTNode findChild(ASTNode parent) {
      ASTNode oldDocument = TreeUtil.findChild(parent, XmlElementType.XML_DOCUMENT);
      if(oldDocument == null) oldDocument = TreeUtil.findChild(parent, XmlElementType.HTML_DOCUMENT);
      return oldDocument;
    }
  };

  public static final RoleFinder ATTRIBUTE_VALUE_FINDER = new DefaultRoleFinder(XmlElementType.XML_ATTRIBUTE_VALUE, null);
  public static final RoleFinder CLOSING_TAG_START_FINDER = new DefaultRoleFinder(XmlTokenType.XML_END_TAG_START, null);
  public static final RoleFinder EMPTY_TAG_END_FINDER = new DefaultRoleFinder(XmlTokenType.XML_EMPTY_ELEMENT_END, null);
  public static final RoleFinder ATTRIBUTE_NAME_FINDER = new DefaultRoleFinder(XmlTokenType.XML_NAME, null);
  public static final RoleFinder ATTRIBUTE_VALUE_VALUE_FINDER = new DefaultRoleFinder(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, null);
  public static final RoleFinder START_TAG_END_FINDER = new DefaultRoleFinder(XmlTokenType.XML_TAG_END, null);
  public static final RoleFinder PROLOG_FINDER = new DefaultRoleFinder(XmlElementType.XML_PROLOG, null);

}

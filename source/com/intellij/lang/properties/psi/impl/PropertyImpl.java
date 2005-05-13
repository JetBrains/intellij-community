package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 9:15:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertyImpl extends PropertiesElementImpl implements Property {
  public PropertyImpl(final ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Property";
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    ASTNode keyNode = getKeyNode();
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), name,"xxx");
    if (keyNode == null) {
      getNode().addChild(property.getKeyNode());
    }
    else {
      getNode().replaceChild(keyNode, property.getKeyNode());
    }
    return this;
  }

  public String getName() {
    return getKey();
  }

  public String getKey() {
    final ASTNode node = getKeyNode();
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public ASTNode getKeyNode() {
    return getNode().findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
  }

  public String getValue() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
    if (node == null) {
      return "";
    }
    return node.getText();
  }

  public String getKeyValueSeparator() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public Icon getIcon(int flags) {
    return Icons.PROPERTY_ICON;
  }

  public void delete() throws IncorrectOperationException {
    getParent().getNode().removeChild(getNode());
  }
}

package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;

import javax.swing.*;

abstract public class AbstractDomElementNode extends SimpleNode {
  protected AbstractDomElementNode() {
    super();
  }

  public AbstractDomElementNode(final SimpleNode parent) {
    super(parent);
  }

  abstract public DomElement getDomElement();

  abstract public String getNodeName();

  abstract public String getTagName();

  public Icon getNodeIcon() {
    return null;
  }

  protected String getPropertyName() {
    return getDomElement().getPresentation().getTypeName();
  }

}

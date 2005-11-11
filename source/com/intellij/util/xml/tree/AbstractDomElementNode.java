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

  protected SimpleNode getDomElementNode(final DomElement domElement, final String tagName, final SimpleNode parentNode) {
      return new BaseDomElementNode(domElement, tagName, parentNode);
  };

  abstract public DomElement getDomElement();

  abstract public String getNodeName();

  abstract public String getTagName();

  public Icon getNodeIcon() {
    return null;
  };

  protected String getPropertyName(String tagName) {
    //todo use name policy
    return tagName.replaceAll("-", " ");
  }

}

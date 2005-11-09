package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;

import javax.swing.*;

abstract public class DomElementNode extends SimpleNode {
  protected DomElementNode() {
    super();
  }

  public DomElementNode(final SimpleNode parent) {
    super(parent);
  }

  protected SimpleDomElementNode getDomElementNode(final DomElement domElement, final String tagName, final SimpleNode parentNode) {
      return new SimpleDomElementNode(domElement, tagName, parentNode);
  };

  abstract public String getNodeName();

  public Icon getNodeIcon() {
    return null;
  };

  protected String getPropertyName(String tagName) {
    //todo use name policy
    return tagName.replaceAll("-", " ");
  }
}

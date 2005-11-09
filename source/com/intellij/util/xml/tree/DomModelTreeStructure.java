package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomElement;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private SimpleDomElementNode myRoot;

  public DomModelTreeStructure(DomElement rootElement) {
    myRoot = createRoot(rootElement);
  }

  protected SimpleDomElementNode createRoot(DomElement rootElement) {
    return new SimpleDomElementNode(rootElement);
  }

  public SimpleDomElementNode getRootElement() {
    return myRoot;
  }
}
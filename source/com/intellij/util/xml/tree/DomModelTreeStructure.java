package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomElement;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private BaseDomElementNode myRoot;

  public DomModelTreeStructure(DomElement rootElement) {
    myRoot = createRoot(rootElement);
  }

  protected BaseDomElementNode createRoot(DomElement rootElement) {
    return new BaseDomElementNode(rootElement);
  }

  public BaseDomElementNode getRootElement() {
    return myRoot;
  }
}
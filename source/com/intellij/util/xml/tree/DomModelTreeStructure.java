package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private DomFileElement myFileElement;

  public DomModelTreeStructure(DomFileElement fileElement) {
    myFileElement = fileElement;
  }

  protected BaseDomElementNode createRoot(DomFileElement rootElement) {
    return new BaseDomElementNode(myFileElement.getRootElement());
  }

  public BaseDomElementNode getRootElement() {
    return createRoot(myFileElement);
  }
}
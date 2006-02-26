package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomElement;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private DomElement myDomElement;
  private AbstractDomElementNode myRootNode;

  public DomModelTreeStructure(DomElement fileElement) {
    myDomElement = fileElement;
  }

  protected AbstractDomElementNode createRoot(DomElement rootElement) {
    return new BaseDomElementNode(myDomElement);
  }

  public AbstractDomElementNode getRootElement() {
    if (myRootNode == null) {
      myRootNode = createRoot(myDomElement);
    }
    return myRootNode;
  }


  public DomElement getRootDomElement() {
    return myDomElement;
  }
}
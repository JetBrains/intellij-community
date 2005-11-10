package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import jetbrains.fabrique.ui.treeStructure.SimpleTreeStructure;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private DomFileElement myFileElement;

  public DomModelTreeStructure(DomFileElement fileElement) {
    myFileElement = fileElement;
  }

  protected DomFileElementNode createRoot(DomFileElement rootElement) {
    return new DomFileElementNode(myFileElement);
  }

  public DomFileElementNode getRootElement() {
    return createRoot(myFileElement);
  }
}
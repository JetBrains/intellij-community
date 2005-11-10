package com.intellij.util.xml.tree;

import com.intellij.util.xml.DomFileElement;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

public class DomFileElementNode extends BaseDomElementNode {
  private DomFileElement myFileElement;

  public DomFileElementNode(final DomFileElement fileElement) {
    super(fileElement);

    myFileElement = fileElement;
  }

  public SimpleNode[] getChildren() {
    return doGetChildren(myFileElement.getRootElement());
  }
}

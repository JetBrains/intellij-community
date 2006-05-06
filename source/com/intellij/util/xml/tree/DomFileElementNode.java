/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.tree;

import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomFileElement;

public class DomFileElementNode extends BaseDomElementNode {
  private DomFileElement myFileElement;

  public DomFileElementNode(final DomFileElement fileElement) {
    super(fileElement);

    myFileElement = fileElement;
  }

  public SimpleNode[] getChildren() {
    return doGetChildren(myFileElement.getRootElement());
  }


  public DomFileElement getDomElement() {
    return (DomFileElement)super.getDomElement();
  }
}

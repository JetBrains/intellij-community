package com.intellij.util.xml.tree;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import java.awt.*;
import java.util.List;

public class DomElementsGroupNode extends AbstractDomElementNode {
  private DomElement myModelElement;
  private String myChildrenTagName;
  private DomCollectionChildDescription myChildDescription;

  public DomElementsGroupNode(final DomElement modelElement, DomCollectionChildDescription description) {
    myModelElement = modelElement;
    myChildDescription = description;
    myChildrenTagName = description.getTagName();
  }

  public SimpleNode[] getChildren() {
    if (!myModelElement.isValid()) return NO_CHILDREN;

    final List<? extends DomElement> domChildren = myChildDescription.getValues(myModelElement);
    final SimpleNode[] simpleNodes = new SimpleNode[domChildren.size()];
    for (int i = 0; i < domChildren.size(); i++) {
      simpleNodes[i] = new BaseDomElementNode(domChildren.get(i), (SimpleNode)this);
    }
    return simpleNodes;
  }

  public Object[] getEqualityObjects() {
    return new Object[0];
  }

  protected boolean doUpdate() {
    setUniformIcon(getNodeIcon());

    clearColoredText();
    addColoredFragment(getNodeName(), new SimpleTextAttributes(Font.BOLD, Color.black));
    final int childrenCount = getChildren().length;
    addColoredFragment(" (" + childrenCount + ')', new SimpleTextAttributes(Font.ITALIC, Color.gray));

    return super.doUpdate();
  }

  public String getNodeName() {
    return myChildDescription.getCommonPresentableName(myModelElement);
  }

  public String getTagName() {
    return myChildrenTagName;
  }

  public DomElement getDomElement() {
    return myModelElement;
  }
}

package com.intellij.util.xml.tree;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.javaee.model.ElementPresentationManager;
import jetbrains.fabrique.ui.treeStructure.SimpleNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;

public class DomElementsGroupNode extends AbstractDomElementNode {
  private DomElement myParentElement;
  private String myChildrenTagName;
  private DomCollectionChildDescription myChildDescription;

  public DomElementsGroupNode(final DomElement modelElement, DomCollectionChildDescription description) {
    myParentElement = modelElement;
    myChildDescription = description;
    myChildrenTagName = description.getXmlElementName();
  }

  public SimpleNode[] getChildren() {
    if (!myParentElement.isValid()) return NO_CHILDREN;

    final List<? extends DomElement> domChildren = myChildDescription.getValues(myParentElement);
    final List<SimpleNode> simpleNodes = new ArrayList<SimpleNode>();
    for (DomElement domChild : domChildren) {
      if (shouldBeShowed(domChild.getDomElementType())) {
        simpleNodes.add(new BaseDomElementNode(domChild, this));
      }
    }
    return simpleNodes.toArray(new SimpleNode[simpleNodes.size()]);
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myParentElement, myChildrenTagName};
  }

  protected boolean doUpdate() {
    setUniformIcon(getNodeIcon());

    clearColoredText();
    addColoredFragment(getNodeName(), new SimpleTextAttributes(Font.BOLD, Color.black));
    final int childrenCount = getChildren().length;
    addColoredFragment(" (" + childrenCount + ')', new SimpleTextAttributes(Font.ITALIC, Color.gray));

    return true;
  }

  public String getNodeName() {
    return myChildDescription.getCommonPresentableName(myParentElement);
  }

  public String getTagName() {
    return myChildrenTagName;
  }

  public DomElement getDomElement() {
    return myParentElement;
  }


  public DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }

  public Icon getNodeIcon() {
    return ElementPresentationManager.getPresentationForClass(DomUtil.getRawType(myChildDescription.getType())).getIcon();
  }
}

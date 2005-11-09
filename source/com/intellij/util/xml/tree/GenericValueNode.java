package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.GenericValue;
import com.intellij.ui.SimpleTextAttributes;

public class GenericValueNode extends AbstractDomElementNode {
  protected GenericValue myModelElement;
  protected String myTagName;

  public GenericValueNode(final GenericValue modelElement, final String tagName, SimpleNode parent) {
    super(parent);

    myModelElement = modelElement;
    myTagName = tagName == null ? "unknown" : tagName;
   }

  public String getNodeName() {
    return getPropertyName(myTagName);
  }

  protected boolean doUpdate() {
    setUniformIcon(getNodeIcon());
    clearColoredText();
    if (myModelElement.getStringValue() != null) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("\"" + myModelElement.getStringValue() + "\"", SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    return super.doUpdate();
  }

  public SimpleNode[] getChildren() {
    return new SimpleNode[0];
  }

  public Object[] getEqualityObjects() {
    return new Object[0];
  }
}

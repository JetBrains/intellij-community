package com.intellij.util.xml.tree;

import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.DomElement;
import com.intellij.ui.SimpleTextAttributes;

public class GenericValueNode extends AbstractDomElementNode {
  protected GenericDomValue myModelElement;
  protected String myTagName;

  public GenericValueNode(final GenericDomValue modelElement, SimpleNode parent) {
    super(parent);

    myModelElement = modelElement;
    myTagName = modelElement.getXmlElementName();
   }

  public String getNodeName() {
    return getPropertyName();
  }

  public String getTagName() {
    return myTagName;
  }

  public DomElement getDomElement() {
    return myModelElement;
  }

  protected void doUpdate() {
    setUniformIcon(getNodeIcon());
    clearColoredText();
    final String stringValue = myModelElement.getStringValue();
    final Object value = myModelElement.getValue();
    if (value instanceof Boolean) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment(String.valueOf(value), SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else if (stringValue != null) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("\"" + stringValue + "\"", SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  public Object[] getEqualityObjects() {
    return NONE;
  }
}

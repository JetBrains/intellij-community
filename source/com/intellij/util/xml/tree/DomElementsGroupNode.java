package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.awt.*;

public class DomElementsGroupNode extends AbstractDomElementNode {
  Logger LOG = Logger.getInstance(DomModelTreeStructure.class.getName());

  private DomElement myModelElement;
  private Method myMethod;
  private String myChildrenTagName;

  public DomElementsGroupNode(final DomElement modelElement, Method method) {
    myModelElement = modelElement;
    myMethod = method;

    myChildrenTagName = modelElement.getMethodsInfo().getTagName(method);
  }

  public SimpleNode[] getChildren() {
    if (!myModelElement.isValid()) return NO_CHILDREN;
    List<SimpleNode> children = new ArrayList();
    try {
      final List<? extends DomElement> objects = (List<? extends DomElement>)myMethod.invoke(myModelElement, new Object[0]);
      for (DomElement domElement : objects) {
        children.add(getDomElementNode(domElement, myChildrenTagName, this));
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return children.toArray(new SimpleNode[children.size()]);
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
    return getPropertyName(StringUtil.pluralize(myChildrenTagName));
  }

  public String getTagName() {
    return myChildrenTagName;
  }
}

package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericValue;

import java.util.*;
import java.lang.reflect.Method;


public class BaseDomElementNode extends AbstractDomElementNode {
  static final private Logger LOG = Logger.getInstance(DomModelTreeStructure.class.getName());

  protected DomElement myDomElement;
  protected String myTagName;

  public BaseDomElementNode(final DomElement modelElement) {
    this(modelElement, null, null);
  }

  public BaseDomElementNode(final DomElement modelElement, final String tagName, SimpleNode parent) {
    super(parent);

    myDomElement = modelElement;
    myTagName = tagName == null ? "unknown" : tagName;
   }

  public SimpleNode[] getChildren() {
    List<SimpleNode> children = new ArrayList();

    final Collection<Method> methods = myDomElement.getMethodsInfo().getFixedChildrenGetterMethods();

    for (Method method : methods) {
      try {
        final Object result = method.invoke(myDomElement, new Object[0]);

        if (result instanceof DomElement) {
          final String tagName = myDomElement.getMethodsInfo().getTagName(method);

          if (showGenericValues() && result instanceof GenericValue) {
             children.add(new GenericValueNode((GenericValue)result, tagName, this));
          } else {

            children.add(getDomElementNode((DomElement)result, tagName, this));
          }
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    final Collection<Method> collectionMethods = myDomElement.getMethodsInfo().getCollectionChildrenGetterMethods();

    for (Method method : collectionMethods) {
      children.add(getDomElementsGroupNode(myDomElement, method));
    }

    AbstractDomElementNode[] childrenNodes = children.toArray(new AbstractDomElementNode[children.size()]);
    if (showOrdered()) {
      Arrays.sort(childrenNodes, getComparator());
    }

    return childrenNodes;
  }

  protected DomElementsGroupNode getDomElementsGroupNode(final DomElement domElement, final Method method) {
    return new DomElementsGroupNode(domElement, method);
  }

  protected boolean showOrdered() {
    return true;
  }

  protected boolean showGenericValues() {
    return true;
  }

  public Object[] getEqualityObjects() {
    return NONE;
  }

  protected boolean doUpdate() {
    setUniformIcon(getNodeIcon());
    clearColoredText();
    addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    return super.doUpdate();
  }

  public String getNodeName() {
    return getPropertyName(myTagName);
  }

  protected Comparator<AbstractDomElementNode> getComparator() {
    return new Comparator<AbstractDomElementNode>() {
      public int compare(final AbstractDomElementNode node1, final AbstractDomElementNode node2) {
        return node1.getNodeName().toLowerCase().compareTo(node2.getNodeName().toLowerCase());
      }
    };
  }

  public boolean isAutoExpandNode() {
    return getParent() == null;
  }
}

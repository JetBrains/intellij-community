package com.intellij.util.xmlb;

import org.jetbrains.annotations.NonNls;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;

//todo: use TagBinding
class OptionTagBinding implements Binding {
  private Accessor accessor;
  private String myName;
  private Binding myBinding;

  public OptionTagBinding(Accessor accessor, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myName = accessor.getName();
    myBinding = xmlSerializer.getBinding(accessor);
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    Element targetElement = ownerDocument.createElement(Constants.OPTION);
    Object value = accessor.read(o);

    targetElement.setAttribute(Constants.NAME, myName);

    if (value == null) return targetElement;

    Node node = myBinding.serialize(value, targetElement);
    if (node instanceof Text) {
      Text text = (Text)node;
      targetElement.setAttribute(Constants.VALUE, text.getWholeText());
    }
    else {
      targetElement.appendChild(node);
    }

    return targetElement;
  }

  public Object deserialize(Object o, Node... nodes) {
    assert nodes.length == 1;
    Element element = ((Element)nodes[0]);
    Attr valueAttr = element.getAttributeNode(Constants.VALUE);

    if (valueAttr != null) {
      Object value = myBinding.deserialize(o, valueAttr);
      accessor.write(o, value);
    }
    else {
      NodeList nodeList = element.getChildNodes();
      List<Node> children = new ArrayList<Node>();

      for (int i = 0; i < nodeList.getLength(); i++) {
        final Node child = nodeList.item(i);
        if (XmlSerializerImpl.isIgnoredNode(child)) continue;
        children.add(child);
      }

      if (children.size() > 0) {
        Object value = myBinding.deserialize(accessor.read(o), children.toArray(new Node[children.size()]));
        accessor.write(o, value);
      }
      else {
        accessor.write(o, null);
      }
    }

    return o;
  }

  public boolean isBoundTo(Node node) {
    if (!(node instanceof Element)) return false;
    Element e = (Element)node;
    if (!e.getNodeName().equals(Constants.OPTION)) return false;
    String name = e.getAttribute(Constants.NAME);
    return name != null && name.equals(myName);
  }

  public Class<? extends Node> getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  public void init() {
  }


  @NonNls
  public String toString() {
    return "OptionTagBinding[" + myName + ", binding=" + myBinding + "]";
  }
}

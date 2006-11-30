package com.intellij.util.xmlb;

import org.w3c.dom.*;

class OptionTagBinding implements Binding {
  private Accessor accessor;
  private String myName;
  private Binding myBinding;

  public OptionTagBinding(Accessor accessor, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myName = accessor.getName();
    myBinding = xmlSerializer.getBinding(accessor.getGenericType());
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

  public Object deserialize(Object o, Node node) {
    Element element = ((Element)node);
    Attr valueAttr = element.getAttributeNode(Constants.VALUE);

    if (valueAttr != null) {
      Object value = myBinding.deserialize(o, valueAttr);
      accessor.write(o, value);
    }
    else {
      Node valueNode = element.getChildNodes().item(0);
      Object value = valueNode != null ? myBinding.deserialize(accessor.read(o), valueNode) : null;
      accessor.write(o, value);
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
}

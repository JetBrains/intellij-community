package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

class PrimitiveValueBinding implements Binding {
  private final Class<?> myType;


  public PrimitiveValueBinding(Class<?> myType) {
    this.myType = myType;
  }

  public Node serialize(Object o, Node context) {
    Document ownerDocument = XmlSerializerImpl.getOwnerDocument(context);
    return ownerDocument.createTextNode(String.valueOf(o));
  }

  @Nullable
  public Object deserialize(Object o, Node... nodes) {
    if (nodes.length == 0) {
      return XmlSerializerImpl.convert("", myType);
    }

    assert nodes.length == 1;

    return XmlSerializerImpl.convert(nodes[0].getNodeValue(), myType);
  }

  public boolean isBoundTo(Node node) {
    throw new UnsupportedOperationException("Method isBoundTo is not supported in " + getClass());
  }

  public Class<? extends Node> getBoundNodeType() {
    return Text.class;
  }

  public void init() {
  }
}
